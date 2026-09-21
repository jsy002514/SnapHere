package com.snaphere.api.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 목록 기간 창 — PST-034, PST-035 */
class PostFeedPeriodTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-02T12:00:00+09:00");

    @Test
    @DisplayName("기간은 조회 시점 기준 롤링 윈도우다 — 주 경계가 아니다")
    void 롤링_윈도우() {
        assertThat(PostFeedPeriod.HOURS_24.from(NOW))
                .isEqualTo(OffsetDateTime.parse("2026-09-01T12:00:00+09:00"));
        assertThat(PostFeedPeriod.WEEKLY.from(NOW))
                .isEqualTo(OffsetDateTime.parse("2026-08-26T12:00:00+09:00"));
        assertThat(PostFeedPeriod.MONTHLY.from(NOW))
                .isEqualTo(OffsetDateTime.parse("2026-08-03T12:00:00+09:00"));
    }

    @Test
    @DisplayName("ALL 은 시간 조건을 걸지 않는다")
    void 전체_기간() {
        assertThat(PostFeedPeriod.ALL.from(NOW)).isNull();
    }

    @Test
    @DisplayName("기본값은 WEEKLY — 명세 API-PST-004")
    void 기본값() {
        assertThat(PostFeedPeriod.DEFAULT).isEqualTo(PostFeedPeriod.WEEKLY);
    }

    @Test
    @DisplayName("값 이름은 명세의 period enum 과 같다")
    void 값_이름() {
        assertThat(PostFeedPeriod.values())
                .extracting(Enum::name)
                .containsExactly("HOURS_24", "WEEKLY", "MONTHLY", "ALL");
    }
}
