package com.ssafy.snaphere.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 반경·개수 파라미터 보정. 검색어 변환은 FulltextQueryTest 로 옮겼다. */
class PlaceServiceQueryTest {

    @Test
    @DisplayName("clamp — 반경·개수 파라미터는 항상 범위 안으로 접힌다")
    void clamp() {
        assertThat(PlaceService.clamp(0, 1, 50)).isEqualTo(1);
        assertThat(PlaceService.clamp(999, 1, 50)).isEqualTo(50);
        assertThat(PlaceService.clamp(20, 1, 50)).isEqualTo(20);
        assertThat(PlaceService.clamp(-100, 1, 20000)).isEqualTo(1);
    }
}
