package com.ssafy.snaphere.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검색어 → ngram FULLTEXT BOOLEAN MODE 변환.
 *
 * 사용자 입력을 그대로 MATCH ... AGAINST 에 넣으면 연산자 문자가
 * SQL 을 깨뜨리거나 의도와 정반대로 동작한다(예: "-서울" = 서울 제외).
 */
class FulltextQueryTest {

    @Test
    @DisplayName("단어마다 +와 접미 와일드카드를 붙여 AND 검색으로 만든다")
    void basicConversion() {
        assertThat(FulltextQuery.toBooleanMode("경복궁")).isEqualTo("+경복궁*");
        assertThat(FulltextQuery.toBooleanMode("서울 종로")).isEqualTo("+서울* +종로*");
    }

    @Test
    @DisplayName("연산자 문자를 제거한다 — 안 하면 검색이 깨지거나 반대로 동작한다")
    void stripsOperators() {
        assertThat(FulltextQuery.toBooleanMode("-서울")).isEqualTo("+서울*");
        assertThat(FulltextQuery.toBooleanMode("\"경복궁\"")).isEqualTo("+경복궁*");
        assertThat(FulltextQuery.toBooleanMode("서울+++종로")).isEqualTo("+서울* +종로*");
        assertThat(FulltextQuery.toBooleanMode("(서울)")).isEqualTo("+서울*");
    }

    @Test
    @DisplayName("ngram 최소 토큰 길이(2) 미만 단어는 버린다")
    void dropsTooShortTokens() {
        assertThat(FulltextQuery.toBooleanMode("서울 역")).isEqualTo("+서울*");
        assertThat(FulltextQuery.toBooleanMode("역")).isNull();
    }

    @Test
    @DisplayName("빈 입력은 null — 호출부가 검색을 건너뛴다. null 을 AGAINST 에 넣으면 오류가 난다")
    void nullForEmpty() {
        assertThat(FulltextQuery.toBooleanMode(null)).isNull();
        assertThat(FulltextQuery.toBooleanMode("   ")).isNull();
        assertThat(FulltextQuery.toBooleanMode("+++")).isNull();
    }
}
