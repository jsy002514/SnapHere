package com.ssafy.snaphere.domain.post.service;

import com.ssafy.snaphere.domain.post.entity.PostSource;
import com.ssafy.snaphere.domain.post.entity.PostTier;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 위치 신뢰도 판정. 이 클래스가 이 프로젝트에서 가장 중요한 비즈니스 로직이다.
 *
 * 순수 함수로 만든 이유
 *   · DB·시간·외부 상태에 의존하지 않아 단위 테스트로 모든 경계를 검증할 수 있다.
 *   · now 를 인자로 받으므로 "10분 경계", "30일 경계" 를 시계 조작 없이 테스트할 수 있다.
 *
 * 판정 규칙 (docs/03_API명세서.md 8장과 1:1)
 *   1. 좌표 없음                                          → NO_LOCATION
 *   2. distance = 장소 중심과 촬영지점 거리
 *   3. distance > verifyRadius (관광지 500m / 사용자장소 100m) → NO_LOCATION
 *   4. source=CAMERA AND |now - takenAt| <= 10분           → ON_SITE
 *   5. takenAt 이 30일 이내                                → LOCATION_CONFIRMED
 *   6. 그 외                                               → NO_LOCATION
 *
 * ⚠️ 프론트가 보낸 tier 는 무시한다. 이 클래스만이 tier 를 만든다.
 */
public final class TierEvaluator {

    private TierEvaluator() {}

    /** 판정에 쓰인 근거를 함께 돌려준다. 로그·응답에서 "왜 이 등급인지" 설명할 수 있어야 한다. */
    public record Result(PostTier tier, Integer distanceMeters, String reason) {

        public boolean countsForRanking() { return tier.countsForRanking(); }
        public boolean createsVisit()     { return tier.createsVisit(); }
    }

    /**
     * @param distanceMeters 장소 중심과 촬영지점 거리(m). 좌표나 장소가 없으면 null
     * @param verifyRadiusM  장소의 인증 반경(m)
     * @param source         촬영 방식
     * @param takenAt        EXIF 촬영 시각. 없으면 null
     * @param now            판정 시각
     */
    public static Result evaluate(Integer distanceMeters, int verifyRadiusM,
                                  PostSource source, LocalDateTime takenAt,
                                  LocalDateTime now,
                                  int onSiteWindowMinutes, int locationConfirmedDays) {

        if (distanceMeters == null) {
            return new Result(PostTier.NO_LOCATION, null, "좌표 없음");
        }
        if (distanceMeters > verifyRadiusM) {
            return new Result(PostTier.NO_LOCATION, distanceMeters,
                    "인증 반경 초과 (" + distanceMeters + "m > " + verifyRadiusM + "m)");
        }
        if (takenAt == null) {
            // 좌표는 맞지만 촬영시각을 모르면 언제 찍은 사진인지 알 수 없다.
            // 오래된 사진을 현장 인증으로 올리는 것을 막아야 한다.
            return new Result(PostTier.NO_LOCATION, distanceMeters, "촬영시각 없음");
        }

        // 미래 시각 방어.
        // 기기 시계는 몇 분 정도 어긋날 수 있으므로 그만큼은 허용하되,
        // 그보다 큰 미래 값은 시계를 조작해 배지를 얻으려는 시도로 본다.
        // (이 검사가 없으면 takenAt = now + 25일 이 abs 때문에 LOCATION_CONFIRMED 로 통과한다)
        long minutesIntoFuture = Duration.between(now, takenAt).toMinutes();
        if (minutesIntoFuture > onSiteWindowMinutes) {
            return new Result(PostTier.NO_LOCATION, distanceMeters,
                    "촬영시각이 미래 (" + minutesIntoFuture + "분 후)");
        }

        long minutesSinceTaken = Math.abs(Duration.between(takenAt, now).toMinutes());

        if (source == PostSource.CAMERA && minutesSinceTaken <= onSiteWindowMinutes) {
            return new Result(PostTier.ON_SITE, distanceMeters,
                    "앱 카메라 촬영 " + minutesSinceTaken + "분 경과");
        }

        long daysSinceTaken = Math.abs(Duration.between(takenAt, now).toDays());
        if (daysSinceTaken <= locationConfirmedDays) {
            return new Result(PostTier.LOCATION_CONFIRMED, distanceMeters,
                    "촬영 후 " + daysSinceTaken + "일 경과");
        }

        return new Result(PostTier.NO_LOCATION, distanceMeters,
                "촬영 시점이 너무 오래됨 (" + daysSinceTaken + "일)");
    }
}
