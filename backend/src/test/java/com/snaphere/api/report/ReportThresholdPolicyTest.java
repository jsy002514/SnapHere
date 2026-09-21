package com.snaphere.api.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 신고 누적 기준 — PST-045 */
class ReportThresholdPolicyTest {

    @Test
    @DisplayName("3건에 도달하면 가린다")
    void 기준_도달() {
        assertThat(ReportThresholdPolicy.shouldBlindPost(3)).isTrue();
    }

    @Test
    @DisplayName("2건까지는 가리지 않는다")
    void 기준_미달() {
        assertThat(ReportThresholdPolicy.shouldBlindPost(0)).isFalse();
        assertThat(ReportThresholdPolicy.shouldBlindPost(1)).isFalse();
        assertThat(ReportThresholdPolicy.shouldBlindPost(2)).isFalse();
    }

    @Test
    @DisplayName("3건을 넘어도 계속 가린 상태로 본다")
    void 기준_초과() {
        assertThat(ReportThresholdPolicy.shouldBlindPost(4)).isTrue();
        assertThat(ReportThresholdPolicy.shouldBlindPost(100)).isTrue();
    }

    @Test
    @DisplayName("기준은 3건이다 — 요구사항 값과 코드가 갈리지 않게 고정한다")
    void 기준값() {
        assertThat(ReportThresholdPolicy.POST_BLIND_THRESHOLD).isEqualTo(3);
    }
}
