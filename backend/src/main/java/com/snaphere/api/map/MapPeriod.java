package com.snaphere.api.map;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;

import java.time.Duration;
import java.time.OffsetDateTime;

public enum MapPeriod {
    LAST_1H(Duration.ofHours(1), Duration.ofMinutes(1), "HOURS_24"),
    LAST_24H(Duration.ofHours(24), Duration.ofMinutes(10), "HOURS_24"),
    WEEKLY(Duration.ofDays(7), Duration.ofMinutes(10), "WEEKLY"),
    MONTHLY(Duration.ofDays(30), Duration.ofMinutes(10), "MONTHLY");

    private final Duration window;
    private final Duration refreshInterval;
    private final String rankingPeriod;

    MapPeriod(Duration window, Duration refreshInterval, String rankingPeriod) {
        this.window = window;
        this.refreshInterval = refreshInterval;
        this.rankingPeriod = rankingPeriod;
    }

    public OffsetDateTime from(OffsetDateTime now) { return now.minus(window); }
    public Duration refreshInterval() { return refreshInterval; }
    public String rankingPeriod() { return rankingPeriod; }

    public static MapPeriod parse(String value) {
        if (value == null || value.isBlank()) return WEEKLY;
        try { return valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { throw new ApiException(ErrorCode.COMMON_400); }
    }
}
