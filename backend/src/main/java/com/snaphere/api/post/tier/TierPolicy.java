package com.snaphere.api.post.tier;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 위치 신뢰 등급 판정 규칙. 상태가 없는 순수 함수다. (PST-022 ~ PST-025)
 *
 * <p>판정 기준은 세 가지뿐이다.
 * <ol>
 *   <li>촬영 좌표가 있는가</li>
 *   <li>그 좌표가 장소의 인증 반경 안인가</li>
 *   <li>촬영한 지 며칠 지났는가</li>
 * </ol>
 *
 * <p>순서가 중요하다. 좌표가 없으면 거리를 볼 수 없고, 반경 밖이면 촬영 시각을 볼 필요가 없다.
 * DB(PostGIS)가 거리를 계산하든 자바가 계산하든 이 클래스는 거리 값만 받으므로 바뀌지 않는다.
 */
public final class TierPolicy {

    private TierPolicy() {
    }

    public static TierDecision decide(TierInput input, TierThresholds thresholds) {
        // 장소에 좌표가 없으면 판정 자체가 불가능하다 (PLC-007)
        if (!input.placeHasCoordinate()) {
            return low(input, thresholds, TierReason.PLACE_HAS_NO_COORDINATE, null,
                    List.of(TierImprovementHint.TAKE_CLOSER_TO_PLACE));
        }

        // ① 촬영 좌표가 없다 → 낮음 (PST-025)
        if (input.distanceM() == null) {
            return low(input, thresholds, TierReason.NO_TAKEN_COORDINATE, null,
                    List.of(TierImprovementHint.ENABLE_LOCATION_ON_CAMERA,
                            TierImprovementHint.SHOOT_NOW_WITH_CAMERA));
        }

        Long daysSinceTaken = daysSinceTaken(input);

        // ② 반경 밖이다 → 낮음 (PST-025)
        if (input.distanceM() > input.appliedRadiusM()) {
            return low(input, thresholds, TierReason.OUT_OF_RADIUS, daysSinceTaken,
                    List.of(TierImprovementHint.TAKE_CLOSER_TO_PLACE));
        }

        // 촬영 시각을 모르면 경과일을 볼 수 없다 → 낮음
        if (input.takenAt() == null) {
            return low(input, thresholds, TierReason.NO_TAKEN_AT, null,
                    List.of(TierImprovementHint.SHOOT_NOW_WITH_CAMERA));
        }

        // ③-1 카메라로 그 자리에서 찍었다 → 높음 (PST-023)
        boolean freshCameraShot = input.source() == PhotoSource.CAMERA
                && withinMinutes(input, thresholds.highWithinMinutes());
        if (freshCameraShot) {
            return new TierDecision(TrustTier.HIGH, TierReason.ON_SITE_CAMERA, true,
                    input.distanceM(), input.appliedRadiusM(), daysSinceTaken, thresholds,
                    List.of(), input.decidedAt());
        }

        // ③-2 30일 이내 촬영이다 → 보통 (PST-024)
        if (daysSinceTaken != null && daysSinceTaken <= thresholds.mediumWithinDays()) {
            return new TierDecision(TrustTier.MEDIUM, TierReason.RECENT_WITHIN_RADIUS, true,
                    input.distanceM(), input.appliedRadiusM(), daysSinceTaken, thresholds,
                    List.of(TierImprovementHint.SHOOT_NOW_WITH_CAMERA), input.decidedAt());
        }

        // ③-3 반경 안이지만 오래된 사진이다 → 낮음 (PST-025)
        return low(input, thresholds, TierReason.TAKEN_TOO_LONG_AGO, daysSinceTaken,
                List.of(TierImprovementHint.USE_RECENT_PHOTO,
                        TierImprovementHint.SHOOT_NOW_WITH_CAMERA));
    }

    private static TierDecision low(TierInput input, TierThresholds thresholds, TierReason reason,
                                    Long daysSinceTaken, List<TierImprovementHint> hints) {
        return new TierDecision(TrustTier.LOW, reason, input.distanceM() != null,
                input.distanceM(), input.appliedRadiusM(), daysSinceTaken, thresholds,
                new ArrayList<>(hints), input.decidedAt());
    }

    private static boolean withinMinutes(TierInput input, int minutes) {
        Duration elapsed = Duration.between(input.takenAt(), input.decidedAt());
        // 미래 시각은 0 으로 취급한다. 기기 시계가 앞서 있는 경우가 실제로 있다
        if (elapsed.isNegative()) {
            elapsed = Duration.ZERO;
        }
        return elapsed.toMinutes() <= minutes;
    }

    private static Long daysSinceTaken(TierInput input) {
        if (input.takenAt() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(input.takenAt(), input.decidedAt());
        return Math.max(days, 0L);
    }
}
