package com.ssafy.snaphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.snaphere.domain.user.entity.Grade;
import com.ssafy.snaphere.global.util.GeoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DB 없이 돌아가는 순수 단위 테스트.
 * (컨텍스트 로딩 테스트는 MySQL 이 필요해서 여기 넣지 않았습니다 — S4 에서 Testcontainers 도입 검토)
 */
class SnaphereApplicationTests {

    @Test
    @DisplayName("등급은 인기 지수 구간으로 산출된다")
    void gradeOf() {
        assertThat(Grade.of(0)).isEqualTo(Grade.SEED);
        assertThat(Grade.of(299)).isEqualTo(Grade.SEED);
        assertThat(Grade.of(300)).isEqualTo(Grade.SPROUT);
        assertThat(Grade.of(1842)).isEqualTo(Grade.TREE);
        assertThat(Grade.of(999999)).isEqualTo(Grade.LEGEND);
        assertThat(Grade.LEGEND.nextScore()).isNull();
        assertThat(Grade.TREE.nextScore()).isEqualTo(5000);
    }

    @Test
    @DisplayName("서비스 범위 검증 — 대한민국 밖 좌표는 거른다")
    void isInKorea() {
        assertThat(GeoUtils.isInKorea(37.5796, 126.9770)).isTrue();   // 경복궁
        assertThat(GeoUtils.isInKorea(33.4996, 126.5312)).isTrue();   // 제주
        assertThat(GeoUtils.isInKorea(35.6895, 139.6917)).isFalse();  // 도쿄
        assertThat(GeoUtils.isInKorea(null, 126.9)).isFalse();
    }

    @Test
    @DisplayName("거리 계산 — 경복궁과 광화문은 약 400m")
    void distance() {
        double d = GeoUtils.distanceMeters(37.5796, 126.9770, 37.5759, 126.9769);
        assertThat(d).isBetween(380.0, 440.0);
    }

    @Test
    @DisplayName("히트맵 격자는 줌 레벨로 결정된다")
    void gridLevel() {
        assertThat(GeoUtils.gridLevelOf(3)).isZero();
        assertThat(GeoUtils.gridLevelOf(8)).isEqualTo(1);
        assertThat(GeoUtils.gridLevelOf(11)).isEqualTo(2);
        assertThat(GeoUtils.gridLevelOf(15)).isEqualTo(3);
        assertThat(GeoUtils.gridSizeOf(2)).isEqualTo(0.01);
    }

    @Test
    @DisplayName("WKT 는 SRID 4326 축 순서(위도 경도)를 따른다")
    void wktAxisOrder() {
        // ⚠️ POINT() 함수는 (경도, 위도) 인데 WKT 문자열은 (위도 경도) 다. 반대다.
        assertThat(GeoUtils.toWkt(37.5796, 126.9770)).isEqualTo("POINT(37.5796 126.977)");
    }
}
