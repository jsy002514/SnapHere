package com.snaphere.api.post;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.dto.PostImageRequest;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.report.UploadSuspensionReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 업로드 제한 검사 — PST-029, PST-030, PST-031, PST-032
 *
 * <p>검사 순서를 함께 검증한다. 정지된 사용자에게 "한도 초과"라고 알려 주면 왜 막혔는지
 * 잘못 이해한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadLimitCheckerTest {

    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final long PLACE = 1L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-02T23:00:00+09:00");
    private static final String HASH = "a".repeat(64);

    @Mock private PostRepository posts;
    @Mock private PostImageRepository postImages;
    @Mock private UploadSuspensionReader suspensions;

    private UploadLimitChecker checker;

    @BeforeEach
    void setUp() {
        checker = new UploadLimitChecker(posts, postImages, suspensions);
        when(suspensions.suspendedUntil(USER)).thenReturn(Optional.empty());
        when(posts.countByUserIdAndStatusAndCreatedAtGreaterThanEqual(any(), any(), any()))
                .thenReturn(0L);
        when(posts.countByUserIdAndPlaceIdAndStatusAndCreatedAtGreaterThanEqual(
                any(), anyLong(), any(), any())).thenReturn(0L);
        when(postImages.countSameHashOwnedBy(any(), any())).thenReturn(0L);
    }

    private static PostImageRequest image(String hash) {
        return new PostImageRequest("posts/" + USER + "/a.webp", 1, null, hash);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).errorCode();
    }

    @Test
    @DisplayName("제한이 없으면 통과한다")
    void 통과() {
        checker.check(USER, PLACE, List.of(image(HASH)), NOW);
    }

    @Test
    @DisplayName("업로드 정지 중이면 POST_UPLOAD_SUSPENDED 와 남은 초를 준다")
    void 업로드_정지() {
        when(suspensions.suspendedUntil(USER)).thenReturn(Optional.of(NOW.plusHours(5)));

        assertThatThrownBy(() -> checker.check(USER, PLACE, List.of(image(HASH)), NOW))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> {
                    assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_UPLOAD_SUSPENDED);
                    assertThat(((ApiException) t).retryAfterSec()).isEqualTo(5 * 3600);
                });
        // 정지가 먼저다. 한도를 세러 가지 않는다.
        verify(posts, never()).countByUserIdAndStatusAndCreatedAtGreaterThanEqual(any(), any(), any());
    }

    @Test
    @DisplayName("이미 지난 정지는 막지 않는다")
    void 만료된_정지() {
        when(suspensions.suspendedUntil(USER)).thenReturn(Optional.of(NOW.minusMinutes(1)));
        checker.check(USER, PLACE, List.of(image(HASH)), NOW);
    }

    @Test
    @DisplayName("하루 30개를 채우면 POST_DAILY_LIMIT · 다음 자정까지 남은 초를 준다")
    void 하루_한도() {
        when(posts.countByUserIdAndStatusAndCreatedAtGreaterThanEqual(any(), any(), any()))
                .thenReturn(30L);

        assertThatThrownBy(() -> checker.check(USER, PLACE, List.of(image(HASH)), NOW))
                .satisfies(t -> {
                    assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_DAILY_LIMIT);
                    assertThat(((ApiException) t).retryAfterSec()).isEqualTo(3600);
                });
    }

    @Test
    @DisplayName("같은 장소 3개를 채우면 POST_PLACE_DAILY_LIMIT")
    void 장소_한도() {
        when(posts.countByUserIdAndPlaceIdAndStatusAndCreatedAtGreaterThanEqual(
                any(), anyLong(), any(), any())).thenReturn(3L);

        assertThatThrownBy(() -> checker.check(USER, PLACE, List.of(image(HASH)), NOW))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_PLACE_DAILY_LIMIT));
    }

    @Test
    @DisplayName("본인 계정에 같은 해시가 있으면 POST_DUPLICATE_IMAGE")
    void 중복_이미지() {
        when(postImages.countSameHashOwnedBy(USER, HASH)).thenReturn(1L);

        assertThatThrownBy(() -> checker.check(USER, PLACE, List.of(image(HASH)), NOW))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_DUPLICATE_IMAGE));
    }

    @Test
    @DisplayName("해시를 보내지 않은 사진은 중복 검사를 하지 않는다 — 판정 근거가 없다")
    void 해시_없으면_검사_생략() {
        checker.check(USER, PLACE, List.of(image(null), image("  ")), NOW);
        verify(postImages, never()).countSameHashOwnedBy(eq(USER), any());
    }
}
