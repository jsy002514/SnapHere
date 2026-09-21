package com.snaphere.api.map;

import com.snaphere.api.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapGridTest {
    @Test
    void 줌_경계에서_네_격자_단계를_선택한다() {
        assertThat(MapGrid.forZoom(6).factor()).isEqualTo(1);
        assertThat(MapGrid.forZoom(7).factor()).isEqualTo(10);
        assertThat(MapGrid.forZoom(9).factor()).isEqualTo(10);
        assertThat(MapGrid.forZoom(10).factor()).isEqualTo(100);
        assertThat(MapGrid.forZoom(13).factor()).isEqualTo(100);
        assertThat(MapGrid.forZoom(14).factor()).isEqualTo(1000);
        assertThat(MapGrid.forZoom(22).factor()).isEqualTo(1000);
    }

    @Test
    void 지원하지_않는_줌을_거부한다() {
        assertThatThrownBy(() -> MapGrid.forZoom(-1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MapGrid.forZoom(23)).isInstanceOf(ApiException.class);
    }

    @Test
    void 음수_좌표도_floor로_안정적으로_셀을_고른다() {
        MapGrid grid = MapGrid.forZoom(10);
        assertThat(grid.latIndex(-0.001)).isEqualTo(-1);
        assertThat(grid.centerLat(-1)).isEqualTo(-0.005);
    }
}
