package com.snaphere.api.post.tier;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 등급 판정 결과와 그 근거. 그대로 {@code tier_logs} 에 적재된다. (PST-028)
 *
 * @param tier                 판정 등급
 * @param reason               그 등급을 받은 이유
 * @param hasTakenCoordinate   판정 기준 ① 촬영 좌표가 있었는가
 * @param distanceM            판정 기준 ② 장소 중심에서의 거리. 좌표가 없으면 null
 * @param appliedRadiusM       판정 기준 ② 그때 적용된 인증 반경
 * @param daysSinceTaken       판정 기준 ③ 촬영 후 경과일. 촬영 시각이 없으면 null
 * @param thresholds           판정에 쓰인 임계값 스냅샷
 * @param improvementHints     등급을 올리는 방법. 높음이면 빈 목록 (PST-049)
 * @param decidedAt            판정 시각. 경과 시간 계산의 기준점
 */
public record TierDecision(
        TrustTier tier,
        TierReason reason,
        boolean hasTakenCoordinate,
        Integer distanceM,
        int appliedRadiusM,
        Long daysSinceTaken,
        TierThresholds thresholds,
        List<TierImprovementHint> improvementHints,
        OffsetDateTime decidedAt
) {
    public TierDecision {
        improvementHints = improvementHints == null ? List.of() : List.copyOf(improvementHints);
    }

    public boolean withinRadius() {
        return distanceM != null && distanceM <= appliedRadiusM;
    }
}
