package com.snaphere.api.post.share;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.media.storage.MediaUrlResolver;
import com.snaphere.api.place.entity.PlaceEntity;
import com.snaphere.api.place.repository.PlaceRepository;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.entity.PostImageEntity;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.when;

/**
 * 공유 메타데이터 — CMU-019, CMU-020, CMU-021, CMU-022
 *
 * <p>내려간 게시글의 링크를 막는지, 값이 비어도 링크가 살아 있는지가 이 서비스의 판단이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShareMetadataServiceTest {

    private static final long POST_ID = 7L;
    private static final long PLACE_ID = 3L;
    private static final UUID AUTHOR = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock private PostRepository posts;
    @Mock private PostImageRepository postImages;
    @Mock private PlaceRepository places;
    @Mock private MediaUrlResolver mediaUrls;

    private ShareMetadataService service;

    @BeforeEach
    void setUp() {
        ShareProperties properties = new ShareProperties(
                "https://snaphere.app/", "/p/", "SnapHere", "SnapHere에서 사진을 확인하세요");
        service = new ShareMetadataService(posts, postImages, places, mediaUrls, properties);

        when(posts.findById(POST_ID)).thenReturn(Optional.of(post("한강 야경이 정말 좋았어요")));
        when(places.findById(PLACE_ID)).thenReturn(Optional.of(place("경복궁")));
        when(postImages.findByPostIdInAndSortOrderOrderByPostId(any(), anyShort()))
                .thenReturn(List.of(image("posts/u/1.webp", "https://cdn/thumb/1.webp")));
        when(mediaUrls.publicUrl("posts/u/1.webp")).thenReturn("https://cdn/posts/u/1.webp");
    }

    private static PostEntity post(String content) {
        return PostEntity.create(AUTHOR, PLACE_ID, null, 1, content, TrustTier.HIGH,
                37.5, 127.0, null, PhotoSource.ALBUM);
    }

    private static PlaceEntity place(String title) {
        PlaceEntity place = PlaceEntity.userPlace(title, "서울 종로구", 37.579, 126.977,
                1, null, AUTHOR);
        ReflectionTestUtils.setField(place, "placeId", PLACE_ID);
        return place;
    }

    private static PostImageEntity image(String key, String thumbnailUrl) {
        PostImageEntity image = PostImageEntity.create(POST_ID, key, 1, null, null);
        ReflectionTestUtils.setField(image, "thumbnailUrl", thumbnailUrl);
        return image;
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).errorCode();
    }

    @Test
    @DisplayName("공유 주소는 웹 베이스와 게시글 ID 로 만든다. 슬래시가 겹치지 않는다 (CMU-019)")
    void buildsShareUrl() {
        assertThat(service.metadata(POST_ID).shareUrl()).isEqualTo("https://snaphere.app/p/7");
    }

    @Test
    @DisplayName("제목은 장소 이름이다. 조사를 서버가 붙이지 않는다")
    void titleIsPlaceName() {
        assertThat(service.metadata(POST_ID).title()).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("장소가 없으면 기본 제목으로 대신한다 — 제목이 빈 미리보기는 링크가 깨져 보인다")
    void fallsBackToDefaultTitle() {
        PostEntity noPlace = PostEntity.create(AUTHOR, null, null, 1, "캡션", TrustTier.LOW,
                null, null, null, PhotoSource.ALBUM);
        when(posts.findById(POST_ID)).thenReturn(Optional.of(noPlace));

        assertThat(service.metadata(POST_ID).title()).isEqualTo("SnapHere");
    }

    @Test
    @DisplayName("설명은 캡션을 한 줄로 눌러 쓰고, 없으면 기본 문장이다 (CMU-020)")
    void description() {
        assertThat(service.metadata(POST_ID).description()).isEqualTo("한강 야경이 정말 좋았어요");

        when(posts.findById(POST_ID)).thenReturn(Optional.of(post("   ")));
        assertThat(service.metadata(POST_ID).description())
                .isEqualTo("SnapHere에서 사진을 확인하세요");
    }

    @Test
    @DisplayName("긴 캡션은 120자에서 자르고 줄임표를 붙인다")
    void truncatesLongDescription() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post("가".repeat(500))));

        String description = service.metadata(POST_ID).description();

        assertThat(description).hasSize(121);
        assertThat(description).endsWith("…");
    }

    @Test
    @DisplayName("줄바꿈이 섞인 캡션은 한 줄로 눌러 쓴다 — OG 설명은 한 줄이다")
    void flattensWhitespace() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post("첫 줄\n\n둘째  줄")));

        assertThat(service.metadata(POST_ID).description()).isEqualTo("첫 줄 둘째 줄");
    }

    @Test
    @DisplayName("대표 이미지는 첫 장의 원본이다. 썸네일은 카드에서 흐려 보인다")
    void coverImageIsOriginal() {
        assertThat(service.metadata(POST_ID).imageUrl()).isEqualTo("https://cdn/posts/u/1.webp");
    }

    @Test
    @DisplayName("사진이 아직 없으면 imageUrl 만 null 이고 링크는 살아 있다")
    void missingImageDoesNotFail() {
        when(postImages.findByPostIdInAndSortOrderOrderByPostId(any(), anyShort()))
                .thenReturn(List.of());

        ShareMetadataResponse metadata = service.metadata(POST_ID);

        assertThat(metadata.imageUrl()).isNull();
        assertThat(metadata.shareUrl()).isNotBlank();
    }

    @Test
    @DisplayName("삭제된 게시글의 공유 주소는 막는다 (CMU-022)")
    void blocksDeletedPost() {
        PostEntity deleted = post("캡션");
        deleted.softDelete();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.metadata(POST_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
    }

    @Test
    @DisplayName("없는 게시글은 POST_NOT_FOUND — 내려간 글과 코드는 다르지만 둘 다 404 다")
    void blocksMissingPost() {
        when(posts.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.metadata(POST_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }
}
