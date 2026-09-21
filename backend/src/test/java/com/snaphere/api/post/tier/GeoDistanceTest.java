package com.snaphere.api.post.tier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 구면 거리 계산 — 반경 판정에 쓰기 충분한 정확도인지 확인 (MAP-030, PST-048) */
class GeoDistanceTest {

    @Test
    @DisplayName("같은 좌표는 0m")
    void 같은_좌표() {
        assertThat(GeoDistance.meters(37.579617, 126.977041, 37.579617, 126.977041)).isZero();
    }

    @Test
    @DisplayName("경복궁 ↔ 광화문광장은 약 400m")
    void 경복궁_광화문() {
        int d = GeoDistance.meters(37.579617, 126.977041, 37.575980, 126.976900);

        assertThat(d).isBetween(380, 430);
    }

    @Test
    @DisplayName("서울 ↔ 부산은 약 325km")
    void 서울_부산() {
        int d = GeoDistance.meters(37.566500, 126.978000, 35.179600, 129.075600);

        assertThat(d).isBetween(320_000, 330_000);
    }

    @Test
    @DisplayName("위도 0.001도는 약 111m — 격자 3단계 기준값과 맞는다 (MAP-009)")
    void 격자_단위() {
        assertThat(GeoDistance.meters(37.500000, 127.0, 37.501000, 127.0)).isBetween(105, 118);
    }

    @Test
    @DisplayName("방향이 바뀌어도 같은 거리")
    void 대칭성() {
        int a = GeoDistance.meters(37.5, 127.0, 35.1, 129.0);
        int b = GeoDistance.meters(35.1, 129.0, 37.5, 127.0);

        assertThat(a).isEqualTo(b);
    }
}
