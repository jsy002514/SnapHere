package com.snaphere.api.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 커서 페이징 크기 규약 — SYS-003 */
class PagingPropertiesTest {

    private final PagingProperties paging = new PagingProperties(20, 50);

    @Test
    @DisplayName("크기를 안 주면 기본값 20")
    void 기본값() {
        assertThat(paging.resolve(null)).isEqualTo(20);
    }

    @Test
    @DisplayName("최대 50 을 넘으면 잘라 낸다 — 실패시키지 않는다")
    void 상한() {
        assertThat(paging.resolve(50)).isEqualTo(50);
        assertThat(paging.resolve(1000)).isEqualTo(50);
    }

    @Test
    @DisplayName("0 이하는 기본값으로 되돌린다")
    void 잘못된_값() {
        assertThat(paging.resolve(0)).isEqualTo(20);
        assertThat(paging.resolve(-1)).isEqualTo(20);
    }

    @Test
    @DisplayName("설정이 비어 있어도 규약 기본값으로 채운다")
    void 설정_누락() {
        PagingProperties empty = new PagingProperties(0, 0);
        assertThat(empty.defaultSize()).isEqualTo(20);
        assertThat(empty.maxSize()).isEqualTo(50);
    }
}
