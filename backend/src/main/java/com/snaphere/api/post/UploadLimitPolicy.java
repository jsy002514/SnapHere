package com.snaphere.api.post;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 업로드 한도 기준값과 기준일 계산. (PST-029, PST-030)
 *
 * <p>기준일은 <b>한국 시간 자정</b>이다 (SYS-005). UTC 자정을 쓰면 한국 사용자에게는 오전 9시에
 * 한도가 초기화되어, 밤에 올린 사진과 다음 날 아침에 올린 사진이 같은 "하루"로 묶인다.
 *
 * <p>DB 도 스프링도 쓰지 않는 순수 계산이라 시각을 넣어 그대로 시험할 수 있다.
 */
public final class UploadLimitPolicy {

    /** 하루 게시글 한도. 초과 시 429. (PST-029) */
    public static final int DAILY_POST_LIMIT = 30;

    /** 같은 장소 하루 한도. 초과 시 429. (PST-030) */
    public static final int PLACE_DAILY_POST_LIMIT = 3;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private UploadLimitPolicy() {
    }

    /**
     * 이 시각이 속한 한국 시간 하루의 시작(자정).
     *
     * <p>{@code count(created_at >= 이 값)} 으로 세면 하루 범위가 된다. 상한을 따로 두지 않는 것은
     * 미래 시각 행이 없기 때문이다 — {@code created_at} 은 서버가 넣는다.
     */
    public static OffsetDateTime startOfDay(OffsetDateTime at) {
        ZonedDateTime kst = at.atZoneSameInstant(KST);
        return kst.toLocalDate().atStartOfDay(KST).toOffsetDateTime();
    }

    /** 다음 한국 시간 자정까지 남은 초. 429 응답의 {@code retryAfterSec} 로 준다. */
    public static int secondsUntilNextDay(OffsetDateTime at) {
        OffsetDateTime next = startOfDay(at).plusDays(1);
        long seconds = Duration.between(at, next).getSeconds();
        return (int) Math.max(1, seconds);
    }

    public static boolean exceedsDailyLimit(long todayCount) {
        return todayCount >= DAILY_POST_LIMIT;
    }

    public static boolean exceedsPlaceDailyLimit(long todayCountAtPlace) {
        return todayCountAtPlace >= PLACE_DAILY_POST_LIMIT;
    }
}
