package com.snaphere.api.post.share;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.media.storage.MediaUrlResolver;
import com.snaphere.api.place.entity.PlaceEntity;
import com.snaphere.api.place.repository.PlaceRepository;
import com.snaphere.api.post.PostStatus;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.entity.PostImageEntity;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * API-PST-014 — 공유 메타데이터.
 *
 * <p>기능 명세: 5.4 공유
 * <p>요구사항: CMU-019, CMU-020, CMU-021, CMU-022
 *
 * <p>공개 주소에 {@code postId} 를 그대로 쓴다. 별도 {@code share_slug} 를 두는 안이 데이터
 * 설계에서 미결정으로 남아 있었는데(CMU-019), 명세의 경로가
 * {@code /public/posts/{postId}/share-metadata} 로 확정돼 있어 그쪽으로 정리했다 — 슬러그는
 * ID 추측을 막는 장치이지만 게시글 목록 자체가 공개라 가릴 것이 없고, 컬럼 하나와 유니크 인덱스
 * 하나가 계속 따라다닌다.
 */
@Service
public class ShareMetadataService {

    /** OG 설명은 미리보기 카드 두 줄 정도다. 더 길면 크롤러가 알아서 자르고 문장이 끊긴다. */
    private static final int MAX_DESCRIPTION_LENGTH = 120;

    /** 대표 사진은 첫 장이다. 정렬 순서는 1부터다 (v1.1.4 정정). */
    private static final short COVER_SORT_ORDER = 1;

    private final PostRepository posts;
    private final PostImageRepository postImages;
    private final PlaceRepository places;
    private final MediaUrlResolver mediaUrls;
    private final ShareProperties properties;

    public ShareMetadataService(PostRepository posts,
                               PostImageRepository postImages,
                               PlaceRepository places,
                               MediaUrlResolver mediaUrls,
                               ShareProperties properties) {
        this.posts = posts;
        this.postImages = postImages;
        this.places = places;
        this.mediaUrls = mediaUrls;
        this.properties = properties;
    }

    /**
     * 공유 카드에 들어갈 값. (CMU-019, CMU-020)
     *
     * <p>삭제·가림 상태면 막는다 (CMU-022). 링크는 카톡·DM 에 한 번 뿌려지면 회수할 수 없으므로,
     * 게시글이 내려간 뒤에도 열리는 주소가 있으면 삭제가 삭제가 아니게 된다.
     *
     * <p>없는 게시글과 내려간 게시글을 다른 코드로 구분하되 둘 다 404 다. 상태 코드가 같아서
     * 링크를 긁는 쪽에는 아무 정보도 주지 않고, 앱은 코드로 "삭제된 글" 안내를 띄울 수 있다.
     */
    @Transactional(readOnly = true)
    public ShareMetadataResponse metadata(long postId) {
        PostEntity post = posts.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new ApiException(ErrorCode.POST_NOT_VISIBLE);
        }

        return new ShareMetadataResponse(
                properties.shareUrl(postId),
                title(post),
                description(post),
                coverImageUrl(postId));
    }

    /**
     * 공유 제목. 장소 이름을 그대로 쓴다.
     *
     * <p>"경복궁에서" 처럼 조사를 붙이지 않는다 — 서버가 문장을 조립하면 언어가 늘어날 때마다
     * 여기를 고쳐야 하고, 조사 규칙은 앞 글자에 따라 달라진다 (SYS-010). 장소가 없는 게시글은
     * 설정된 기본 제목(앱 이름)으로 대신한다: 제목이 빈 미리보기는 링크가 깨진 것처럼 보인다.
     */
    private String title(PostEntity post) {
        if (post.getPlaceId() == null) {
            return properties.defaultTitle();
        }
        return places.findById(post.getPlaceId())
                .map(PlaceEntity::getTitle)
                .filter(name -> name != null && !name.isBlank())
                .orElse(properties.defaultTitle());
    }

    /** 캡션을 미리보기 길이로 줄여 쓴다. 없으면 설정된 기본 문장이다. */
    private String description(PostEntity post) {
        String content = post.getContent();
        if (content == null || content.isBlank()) {
            return properties.defaultDescription();
        }
        String flattened = content.strip().replaceAll("\\s+", " ");
        return flattened.length() <= MAX_DESCRIPTION_LENGTH
                ? flattened
                : flattened.substring(0, MAX_DESCRIPTION_LENGTH).strip() + "…";
    }

    /**
     * 대표 이미지. 첫 장의 원본을 쓰고 없으면 썸네일이다.
     *
     * <p>원본을 먼저 쓰는 이유는 미리보기 카드가 큰 이미지를 원하기 때문이다. 썸네일은 480px
     * 이라 카톡 카드에서 흐려 보인다. 둘 다 없으면 null 로 두고 링크는 그대로 살린다.
     */
    private String coverImageUrl(long postId) {
        List<PostImageEntity> cover =
                postImages.findByPostIdInAndSortOrderOrderByPostId(List.of(postId), COVER_SORT_ORDER);
        return cover.stream()
                .findFirst()
                .map(image -> Optional.ofNullable(image.getImageKey())
                        .map(mediaUrls::publicUrl)
                        .orElse(image.getThumbnailUrl()))
                .orElse(null);
    }
}
