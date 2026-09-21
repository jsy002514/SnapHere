package com.snaphere.api.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 콘텐츠 유형 대응 — CMU-027
 *
 * <p>용어 사전(docs/06-glossary.md &gt; contentTypeId)의 대응을 그대로 지키는지 본다. 이 표가
 * 어긋나면 카테고리 태그와 테마 랭킹이 다른 이름을 쓰게 된다 (RNK-005).
 */
class ContentTypeTest {

    @Test
    @DisplayName("용어 사전의 6개 번호가 그대로 태그 이름이 된다")
    void mapsGlossaryCodes() {
        assertThat(ContentType.of(12).map(ContentType::tagName)).contains("관광지");
        assertThat(ContentType.of(14).map(ContentType::tagName)).contains("문화시설");
        assertThat(ContentType.of(15).map(ContentType::tagName)).contains("축제공연행사");
        assertThat(ContentType.of(28).map(ContentType::tagName)).contains("레포츠");
        assertThat(ContentType.of(38).map(ContentType::tagName)).contains("쇼핑");
        assertThat(ContentType.of(39).map(ContentType::tagName)).contains("음식점");
    }

    @Test
    @DisplayName("모르는 번호와 null 은 빈 값이다 — TourAPI 가 유형을 늘려도 숫자가 태그로 붙지 않는다")
    void unknownCodes() {
        assertThat(ContentType.of(25)).isEmpty();
        assertThat(ContentType.of(null)).isEmpty();
    }
}
