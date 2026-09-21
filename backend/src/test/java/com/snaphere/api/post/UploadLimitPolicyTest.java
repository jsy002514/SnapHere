package com.snaphere.api.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 업로드 한도 기준일 계산 — PST-029, PST-030, SYS-005
 *
 * <p>기준일이 UTC 자정이면 한국 사용자에게는 오전 9시에 한도가 초기화된다. 그 실수를 잡는 테스트다.
 */
class UploadLimitPolicyTest {

    @Test
    @DisplayName("한국 시간 자정을 기준일 시작으로 삼는다")
    void 기준일_한국시간_자정() {
        OffsetDateTime at = OffsetDateTime.parse("2026-09-02T14:30:00+09:00");
        assertThat(UploadLimitPolicy.startOfDay(at))
                .isEqualTo(OffsetDateTime.parse("2026-09-02T00:00:00+09:00"));
    }

    @Test
    @DisplayName("한국 시간 자정 직후는 그날에 속한다")
    void 자정_직후() {
        OffsetDateTime at = OffsetDateTime.parse("2026-09-02T00:00:01+09:00");
        assertThat(UploadLimitPolicy.startOfDay(at))
                .isEqualTo(OffsetDateTime.parse("2026-09-02T00:00:00+09:00"));
    }

    @Test
    @DisplayName("한국 시간 자정 직전은 전날에 속한다")
    void 자정_직전() {
        OffsetDateTime at = OffsetDateTime.parse("2026-09-02T23:59:59+09:00");
        assertThat(UploadLimitPolicy.startOfDay(at))
                .isEqualTo(OffsetDateTime.parse("2026-09-02T00:00:00+09:00"));
    }

    @Test
    @DisplayName("UTC 로 들어온 시각도 한국 시간 기준으로 나눈다")
    void UTC_입력도_한국시간_기준() {
        // 2026-09-02T20:00Z = 한국 시간 2026-09-03 05:00 → 9월 3일에 속한다
        OffsetDateTime at = OffsetDateTime.parse("2026-09-02T20:00:00Z");
        assertThat(UploadLimitPolicy.startOfDay(at))
                .isEqualTo(OffsetDateTime.parse("2026-09-03T00:00:00+09:00"));
    }

    @Test
    @DisplayName("UTC 자정이 아니라 한국 시간 자정에 초기화된다")
    void UTC_자정과_다름() {
        // 한국 시간 오전 8시. UTC 로는 아직 전날 23시라 UTC 기준이면 전날로 묶인다
        OffsetDateTime at = OffsetDateTime.parse("2026-09-02T08:00:00+09:00");
        assertThat(UploadLimitPolicy.startOfDay(at))
                .isEqualTo(OffsetDateTime.parse("2026-09-02T00:00:00+09:00"))
                .isNotEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("다음 자정까지 남은 초를 준다")
    void 재시도_초() {
        OffsetDateTime at = OffsetDateTime.parse("2026-09-02T23:00:00+09:00");
        assertThat(UploadLimitPolicy.secondsUntilNextDay(at)).isEqualTo(3600);
    }

    @Test
    @DisplayName("자정 직전이어도 0 이 아니라 최소 1초를 준다")
    void 재시도_초_최소값() {
        OffsetDateTime at = OffsetDateTime.parse("2026-09-02T23:59:59.900+09:00");
        assertThat(UploadLimitPolicy.secondsUntilNextDay(at)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("하루 한도는 30개 · 장소별 한도는 3개")
    void 한도_경계() {
        assertThat(UploadLimitPolicy.exceedsDailyLimit(29)).isFalse();
        assertThat(UploadLimitPolicy.exceedsDailyLimit(30)).isTrue();
        assertThat(UploadLimitPolicy.exceedsPlaceDailyLimit(2)).isFalse();
        assertThat(UploadLimitPolicy.exceedsPlaceDailyLimit(3)).isTrue();
    }
}
