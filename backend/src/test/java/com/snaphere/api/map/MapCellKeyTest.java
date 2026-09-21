package com.snaphere.api.map;

import com.snaphere.api.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapCellKeyTest {
    @Test
    void 기간과_격자_좌표를_불투명_키로_왕복한다() {
        MapCellKey source = new MapCellKey(MapPeriod.LAST_1H, 3, 37501, 126987);
        assertThat(MapCellKey.decode(source.encode())).isEqualTo(source);
    }

    @Test
    void 손상된_셀_키를_거부한다() {
        assertThatThrownBy(() -> MapCellKey.decode("z2:37.5:126.9")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MapCellKey.decode("hmc_%%%" )).isInstanceOf(ApiException.class);
    }
}
