package com.snaphere.api.media;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.media.dto.PresignFileRequest;
import com.snaphere.api.media.dto.PresignRequest;
import com.snaphere.api.media.dto.UploadUrl;
import com.snaphere.api.media.storage.MediaStorageProperties;
import com.snaphere.api.media.storage.PresignedUrlIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MediaService 검증 규칙 — PST-013, PST-015, USER-004, SYS-020 */
class MediaServiceTest {

    private static final long TEN_MB = 10L * 1024 * 1024;
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private MediaService service;

    @BeforeEach
    void setUp() {
        MediaStorageProperties properties = new MediaStorageProperties(
                "stub", "test-bucket", "ap-northeast-2", "https://cdn.test",
                Duration.ofMinutes(5), TEN_MB);
        PresignedUrlIssuer issuer = (objectKey, contentType, ttl) ->
                new PresignedUrlIssuer.Issued("https://cdn.test/" + objectKey,
                        Map.of("Content-Type", contentType));
        service = new MediaService(issuer, properties);
    }

    private static PresignFileRequest file(String mimeType, long sizeBytes) {
        return new PresignFileRequest(mimeType, sizeBytes);
    }

    private static List<PresignFileRequest> jpegs(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> file("image/jpeg", 1024L))
                .toList();
    }

    @Test
    @DisplayName("게시글 사진 4장까지 발급된다 (PST-013)")
    void 게시글_최대_4장() {
        List<UploadUrl> urls = service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.POST_IMAGE, jpegs(4)));

        assertThat(urls).hasSize(4);
        assertThat(urls).allSatisfy(url -> {
            assertThat(url.imageKey()).startsWith("originals/posts/" + USER_ID + "/");
            assertThat(url.imageKey()).endsWith(".jpg");
            assertThat(url.headers()).containsEntry("Content-Type", "image/jpeg");
        });
        assertThat(urls.stream().map(UploadUrl::imageKey).distinct()).hasSize(4);
    }

    @Test
    @DisplayName("게시글 사진 5장이면 MEDIA_COUNT_INVALID (PST-013)")
    void 게시글_5장은_거부() {
        assertThatThrownBy(() -> service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.POST_IMAGE, jpegs(5))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.MEDIA_COUNT_INVALID);
    }

    @Test
    @DisplayName("프로필 이미지는 1장만 허용한다 (USER-004)")
    void 프로필은_1장만() {
        assertThat(service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.PROFILE_IMAGE, jpegs(1)))).hasSize(1);

        assertThatThrownBy(() -> service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.PROFILE_IMAGE, jpegs(2))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.MEDIA_COUNT_INVALID);
    }

    @Test
    @DisplayName("프로필 이미지는 profile/ 접두어를 쓴다")
    void 프로필_키_접두어() {
        UploadUrl url = service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.PROFILE_IMAGE, jpegs(1))).get(0);

        assertThat(url.imageKey()).startsWith("profile/" + USER_ID + "/");
    }

    @Test
    @DisplayName("허용 목록 밖 형식은 MEDIA_TYPE_UNSUPPORTED (PST-015)")
    void 지원하지_않는_형식() {
        assertThatThrownBy(() -> service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.POST_IMAGE, List.of(file("image/gif", 1024L)))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.MEDIA_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("heic·webp 는 허용한다 (PST-015)")
    void heic_webp_허용() {
        List<UploadUrl> urls = service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.POST_IMAGE,
                        List.of(file("image/heic", 1024L), file("image/webp", 1024L))));

        assertThat(urls).extracting(UploadUrl::imageKey)
                .anySatisfy(key -> assertThat(key).endsWith(".heic"))
                .anySatisfy(key -> assertThat(key).endsWith(".webp"));
    }

    @Test
    @DisplayName("장당 10MB 를 넘으면 MEDIA_TOO_LARGE (PST-015)")
    void 용량_초과() {
        assertThatThrownBy(() -> service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.POST_IMAGE, List.of(file("image/jpeg", TEN_MB + 1)))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.MEDIA_TOO_LARGE);
    }

    @Test
    @DisplayName("10MB 정확히는 통과한다 (경계값)")
    void 용량_경계값() {
        assertThat(service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.POST_IMAGE, List.of(file("image/jpeg", TEN_MB)))))
                .hasSize(1);
    }

    @Test
    @DisplayName("만료 시각은 발급 시점 + 5분이다 (SYS-020)")
    void 만료_5분() {
        UploadUrl url = service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.POST_IMAGE, jpegs(1))).get(0);

        assertThat(url.expiresAt()).isAfter(java.time.OffsetDateTime.now().plusMinutes(4));
        assertThat(url.expiresAt()).isBefore(java.time.OffsetDateTime.now().plusMinutes(6));
    }

    @Test
    @DisplayName("mimeType 대소문자·공백은 정규화해서 받는다")
    void mimeType_정규화() {
        assertThat(service.issueUploadUrls(USER_ID,
                new PresignRequest(MediaPurpose.POST_IMAGE, List.of(file("  IMAGE/JPEG ", 1024L)))))
                .hasSize(1);
    }
}
