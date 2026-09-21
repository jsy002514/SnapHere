package com.snaphere.api.post.dto;

import java.util.List;

/**
 * 명세: 3. 응답 스키마 &gt; CreatePostResult
 *
 * @param visitRecorded 이번 게시로 방문이 새로 기록됐는지 (VST-001). 같은 날 같은 장소에 이미
 *                      기록이 있으면 false (VST-002)
 * @param earnedBadges  이번 요청으로 획득한 뱃지. 반경 밖이어도 게시는 성공하고 이 목록만 빈다
 */
public record CreatePostResponse(
        String postId,
        String mediaStatus,
        TierResultResponse tierResult,
        boolean visitRecorded,
        List<BadgeSummaryResponse> earnedBadges
) {
}
