package com.ssafy.snaphere.domain.tag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 태그 정규화.
 * 정규화가 없으면 "#야경", "야경", "야 경", "Seoul", "seoul" 이 모두 다른 태그가 되어
 * 태그 랭킹과 태그 검색이 무의미해진다.
 */
class TagServiceNormalizeTest {

    @Test
    @DisplayName("앞의 # 을 제거한다 — 사용자가 붙여 써도 같은 태그로 모인다")
    void stripsHash() {
        assertThat(TagService.normalize("#야경")).isEqualTo("야경");
        assertThat(TagService.normalize("##야경")).isEqualTo("야경");
        assertThat(TagService.normalize("  #야경  ")).isEqualTo("야경");
    }

    @Test
    @DisplayName("공백과 쉼표를 제거한다")
    void removesWhitespace() {
        assertThat(TagService.normalize("야 경")).isEqualTo("야경");
        assertThat(TagService.normalize("한옥,마을")).isEqualTo("한옥마을");
    }

    @Test
    @DisplayName("영문은 소문자로 통일한다")
    void lowercases() {
        assertThat(TagService.normalize("Seoul")).isEqualTo("seoul");
        assertThat(TagService.normalize("SEOUL")).isEqualTo("seoul");
    }

    @Test
    @DisplayName("빈 값은 null — 호출부가 건너뛴다")
    void nullForEmpty() {
        assertThat(TagService.normalize(null)).isNull();
        assertThat(TagService.normalize("   ")).isNull();
        assertThat(TagService.normalize("#")).isNull();
        assertThat(TagService.normalize(" # # ")).isNull();
    }

    @Test
    @DisplayName("50자를 넘으면 자른다 — 컬럼 길이가 VARCHAR(50)")
    void truncatesToColumnLength() {
        String long60 = "가".repeat(60);
        assertThat(TagService.normalize(long60)).hasSize(50);
    }
}
