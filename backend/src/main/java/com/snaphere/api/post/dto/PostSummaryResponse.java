package com.snaphere.api.post.dto;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.post.entity.PostEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 명세: 3. 응답 스키마 &gt; PostSummary. 목록 카드가 쓰는 공통 필드.
 *
 * <p>{@code aspectRatio} 는 대표 사진(정렬 1번)의 비율이다. 메이슨리는 이미지를 받기 전에 카드
 * 높이를 잡아야 하므로 이 값이 없으면 스크롤이 튄다 (PST-021).
 *
 * <p>{@code isLiked}·{@code isBookmarked} 는 요청자 기준 상태다. 비회원이면 null 이다 —
 * false 가 아니다. "안 눌렀다"와 "알 수 없다"는 앱에서 다르게 그려진다 (명세상 선택 필드).
 */
public record PostSummaryResponse(
        String postId,
        UserSummaryResponse author,
        PlaceSummaryResponse place,
        String thumbnailUrl,
        int imageCount,
        BigDecimal aspectRatio,
        String tier,
        int likeCount,
        int commentCount,
        OffsetDateTime createdAt,
        Boolean isLiked,
        Boolean isBookmarked
) {
    public static PostSummaryResponse of(PostEntity post,
                                         UserSummaryResponse author,
                                         PlaceSummaryResponse place,
                                         List<PostImageResponse> images,
                                         Boolean isLiked,
                                         Boolean isBookmarked) {
        PostImageResponse cover = images.isEmpty() ? null : images.get(0);
        return new PostSummaryResponse(
                ExternalIds.post(post.getPostId()),
                author,
                place,
                cover == null ? null : cover.thumbnailUrl(),
                images.size(),
                cover == null ? PostImageResponse.DEFAULT_ASPECT_RATIO : cover.aspectRatio(),
                post.getTier().name(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreatedAt(),
                isLiked,
                isBookmarked);
    }
}
