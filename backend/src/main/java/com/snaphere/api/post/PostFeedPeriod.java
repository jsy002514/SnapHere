package com.snaphere.api.post;

import java.time.OffsetDateTime;

/**
 * 목록·인기 조회의 기간 창. (PST-034, PST-035)
 *
 * <p>이름이 집계 주기가 아니라 <b>조회 시점 기준 롤링 윈도우</b>다. {@code WEEKLY} 는 "이번 주"가
 * 아니라 "지금부터 7일 전까지"다. 주 경계를 쓰면 월요일 아침에 인기 목록이 텅 빈다.
 *
 * <p>값 이름은 API 명세의 {@code period} enum 과 데이터 설계의 {@code post_rankings.period} 를
 * 그대로 쓴다. 세 곳이 어긋나면 배치가 채운 행을 조회가 못 찾는다.
 */
public enum PostFeedPeriod {

    HOURS_24,
    WEEKLY,
    MONTHLY,
    /** 기간 제한 없음. */
    ALL;

    /** 명세의 기본값. (API-PST-004) */
    public static final PostFeedPeriod DEFAULT = WEEKLY;

    /** @return 이 기간의 시작 시각. {@link #ALL} 은 null — 시간 조건을 걸지 않는다 */
    public OffsetDateTime from(OffsetDateTime now) {
        return switch (this) {
            case HOURS_24 -> now.minusHours(24);
            case WEEKLY -> now.minusDays(7);
            case MONTHLY -> now.minusDays(30);
            case ALL -> null;
        };
    }
}
