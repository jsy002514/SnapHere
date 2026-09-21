package com.snaphere.api.reaction.dto;

import com.snaphere.api.reaction.LikeTargetType;

/**
 * 명세: 3. 응답 스키마 &gt; LikeResult
 *
 * @param isLiked   요청 <b>후</b>의 상태. 앱이 자기 상태를 되돌려 계산하지 않고 그대로 쓴다
 * @param likeCount 대상의 최신 좋아요 수. 화면 숫자를 서버 값으로 맞춘다
 */
public record LikeResultResponse(
        String targetType,
        String targetId,
        boolean isLiked,
        int likeCount
) {
    public static LikeResultResponse of(LikeTargetType targetType, long targetId,
                                        boolean liked, int likeCount) {
        return new LikeResultResponse(targetType.name(), String.valueOf(targetId), liked, likeCount);
    }
}
