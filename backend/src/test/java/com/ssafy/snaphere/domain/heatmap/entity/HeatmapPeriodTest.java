package com.ssafy.snaphere.domain.heatmap.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 히트맵 기간.
 * 데이터가 적을 때 자동으로 기간을 넓히는 fallback 이 시연에서 지도가 텅 비는 것을 막는다.
 */
class HeatmapPeriodTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 12, 0);

    @Test
    @DisplayName("REALTIME 은 최근 1시간이다")
    void realtimeWindow() {
        assertThat(HeatmapPeriod.REALTIME.from(NOW)).isEqualTo(NOW.minusHours(1));
    }

    @Test
    @DisplayName("ALL 은 기간 제한이 없다 (null)")
    void allHasNoWindow() {
        assertThat(HeatmapPeriod.ALL.from(NOW)).isNull();
    }

    @Test
    @DisplayName("fallback 은 REALTIME → DAY → WEEK → MONTH → ALL 순으로 넓어진다")
    void widenChain() {
        assertThat(HeatmapPeriod.REALTIME.widen()).isEqualTo(HeatmapPeriod.DAY);
        assertThat(HeatmapPeriod.DAY.widen()).isEqualTo(HeatmapPeriod.WEEK);
        assertThat(HeatmapPeriod.WEEK.widen()).isEqualTo(HeatmapPeriod.MONTH);
        assertThat(HeatmapPeriod.MONTH.widen()).isEqualTo(HeatmapPeriod.ALL);
    }

    @Test
    @DisplayName("ALL 에서 더 넓힐 곳은 없다 — 무한 루프를 만들지 않는다")
    void widenTerminates() {
        assertThat(HeatmapPeriod.ALL.widen()).isEqualTo(HeatmapPeriod.ALL);
    }

    @Test
    @DisplayName("잘못된 문자열은 REALTIME 으로 떨어진다 — 예외를 던져 지도를 못 그리게 하지 않는다")
    void unknownFallsBackToRealtime() {
        assertThat(HeatmapPeriod.of("이상한값")).isEqualTo(HeatmapPeriod.REALTIME);
        assertThat(HeatmapPeriod.of(null)).isEqualTo(HeatmapPeriod.REALTIME);
        assertThat(HeatmapPeriod.of("  week  ")).isEqualTo(HeatmapPeriod.WEEK);
    }
}
