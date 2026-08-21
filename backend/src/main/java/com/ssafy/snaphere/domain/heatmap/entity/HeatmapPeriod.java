package com.ssafy.snaphere.domain.heatmap.entity;

import java.time.LocalDateTime;

/**
 * 히트맵 집계 기간.
 *
 * REALTIME 이 홈 지도의 기본 레이어다. "실시간" 의 정의는 확정 사항이다:
 * 웹소켓·SSE 를 쓰지 않고 서버 1분 캐시 + 앱 60초 폴링으로 구현한다.
 * 시간당 유입이 수십 건 수준이라 초 단위 갱신은 화면이 바뀌지 않는다.
 */
public enum HeatmapPeriod {
    REALTIME(60),      // 최근 1시간
    DAY(24 * 60),
    WEEK(7 * 24 * 60),
    MONTH(30 * 24 * 60),
    ALL(0);            // 0 = 기간 제한 없음

    private final int windowMinutes;

    HeatmapPeriod(int windowMinutes) { this.windowMinutes = windowMinutes; }

    public int windowMinutes() { return windowMinutes; }

    /** 집계 시작 시각. ALL 은 제한이 없으므로 null. */
    public LocalDateTime from(LocalDateTime now) {
        return windowMinutes == 0 ? null : now.minusMinutes(windowMinutes);
    }

    /** 데이터가 부족할 때 넓힐 다음 기간. ALL 이면 더 넓힐 곳이 없다. */
    public HeatmapPeriod widen() {
        return switch (this) {
            case REALTIME -> DAY;
            case DAY -> WEEK;
            case WEEK -> MONTH;
            case MONTH, ALL -> ALL;
        };
    }

    public static HeatmapPeriod of(String raw) {
        if (raw == null || raw.isBlank()) return REALTIME;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return REALTIME;
        }
    }
}
