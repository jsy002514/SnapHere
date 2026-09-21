package com.snaphere.api.post.tier;

import java.util.UUID;

/**
 * 판정 근거를 남긴다. (PST-028)
 *
 * <p>심사에서 "위치를 어떻게 검증했는가"의 근거로 쓰이고, 등급 안내 화면(PST-047)이 읽는다.
 * DB 가 들어오면 {@code tier_logs} 테이블에 적재하는 구현으로 바꾼다.
 *
 * @param postId 확정된 게시글 판정이면 게시글 ID, 미리보기(API-PST-002)면 null
 */
public interface TierDecisionLogger {

    void record(Long postId, UUID userId, long placeId, Long eventId,
                TierInput input, TierDecision decision);
}
