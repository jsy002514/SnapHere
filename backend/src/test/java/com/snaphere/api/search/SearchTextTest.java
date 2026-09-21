package com.snaphere.api.search;

import com.snaphere.api.place.PlaceDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextTest {
    private static final List<PlaceDtos.Region> REGIONS = List.of(
            new PlaceDtos.Region(1, "서울", "Seoul", null, 2000),
            new PlaceDtos.Region(33, "충청북도", "Chungcheongbuk-do", null, 2000),
            new PlaceDtos.Region(37, "전북특별자치도", "Jeonbuk-do", null, 2000)
    );

    @Test
    void normalizesWhitespaceCaseAndHashTags() {
        assertThat(SearchText.display("  Seoul   Palace ")).isEqualTo("Seoul Palace");
        assertThat(SearchText.normalize("  Seoul   Palace ")).isEqualTo("seoul palace");
        assertThat(SearchText.normalizeTag(" # 드라마 촬영지 ")).isEqualTo("드라마촬영지");
    }

    @Test
    void recognizesKoreanShortFullAndEnglishRegionNames() {
        assertThat(SearchText.matchedRegion(REGIONS, "서울특별시").areaCode()).isEqualTo(1);
        assertThat(SearchText.matchedRegion(REGIONS, "충북").areaCode()).isEqualTo(33);
        assertThat(SearchText.matchedRegion(REGIONS, "jeonbuk-do").areaCode()).isEqualTo(37);
    }

    @Test
    void doesNotTreatLongerPhraseAsRegionFilter() {
        assertThat(SearchText.matchedRegion(REGIONS, "서울 맛집")).isNull();
    }
}
