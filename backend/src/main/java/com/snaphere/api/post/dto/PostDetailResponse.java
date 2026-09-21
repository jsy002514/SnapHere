package com.snaphere.api.post.dto;

import com.snaphere.api.post.entity.PostEntity;

import java.util.List;
import java.util.Map;

/**
 * 명세: 3. 응답 스키마 &gt; PostDetail
 *
 * @param event        이벤트 참여 정보. {@code events} 테이블이 없어 지금은 항상 null (EVT-016~023)
 * @param translations 후속 번역 확장 필드. 현재는 항상 null (SYS-018)
 */
public record PostDetailResponse(
        PostSummaryResponse summary,
        List<PostImageResponse> images,
        String content,
        String originalLanguageCode,
        Map<String, Object> translations,
        List<TagSummaryResponse> tags,
        Object event,
        TierResultResponse tierResult,
        int viewCount
) {
    public static PostDetailResponse of(PostEntity post,
                                        PostSummaryResponse summary,
                                        List<PostImageResponse> images,
                                        List<TagSummaryResponse> tags,
                                        TierResultResponse tierResult) {
        return new PostDetailResponse(
                summary,
                images,
                post.getContent(),
                post.getOriginalLanguageCode(),
                null,
                tags,
                null,
                tierResult,
                post.getViewCount());
    }
}
