package com.snaphere.api.post.tier;

/**
 * 등급 판정 임계값. 판정 로그에 스냅샷으로 남긴다. (PST-023, PST-024, PST-028)
 *
 * <p>기준이 바뀌어도 과거 판정을 그대로 재현할 수 있도록 값 자체를 기록한다.
 *
 * @param highWithinMinutes 카메라 촬영 후 이 시간 안이면 높음 후보. 기본 10분
 * @param mediumWithinDays  촬영 후 이 기간 안이면 보통 후보. 기본 30일
 */
public record TierThresholds(int highWithinMinutes, int mediumWithinDays) {

    public static final TierThresholds DEFAULT = new TierThresholds(10, 30);

    public TierThresholds {
        if (highWithinMinutes <= 0 || mediumWithinDays <= 0) {
            throw new IllegalArgumentException("임계값은 0보다 커야 한다");
        }
    }
}
