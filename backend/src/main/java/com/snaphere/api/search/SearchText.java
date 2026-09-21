package com.snaphere.api.search;

import com.snaphere.api.place.PlaceDtos;
import com.snaphere.api.post.entity.TagEntity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SearchText {
    private static final Map<Integer, Set<String>> REGION_ALIASES = Map.ofEntries(
            Map.entry(1, Set.of("서울", "서울특별시", "seoul")),
            Map.entry(2, Set.of("인천", "인천광역시", "incheon")),
            Map.entry(3, Set.of("대전", "대전광역시", "daejeon")),
            Map.entry(4, Set.of("대구", "대구광역시", "daegu")),
            Map.entry(5, Set.of("광주", "광주광역시", "gwangju")),
            Map.entry(6, Set.of("부산", "부산광역시", "busan")),
            Map.entry(7, Set.of("울산", "울산광역시", "ulsan")),
            Map.entry(8, Set.of("세종", "세종시", "세종특별자치시", "sejong")),
            Map.entry(31, Set.of("경기", "경기도", "gyeonggi", "gyeonggido")),
            Map.entry(32, Set.of("강원", "강원도", "강원특별자치도", "gangwon", "gangwondo")),
            Map.entry(33, Set.of("충북", "충청북도", "chungbuk", "chungcheongbuk", "chungcheongbukdo")),
            Map.entry(34, Set.of("충남", "충청남도", "chungnam", "chungcheongnam", "chungcheongnamdo")),
            Map.entry(35, Set.of("경북", "경상북도", "gyeongbuk", "gyeongsangbuk", "gyeongsangbukdo")),
            Map.entry(36, Set.of("경남", "경상남도", "gyeongnam", "gyeongsangnam", "gyeongsangnamdo")),
            Map.entry(37, Set.of("전북", "전라북도", "전북특별자치도", "jeonbuk", "jeonbukdo")),
            Map.entry(38, Set.of("전남", "전라남도", "jeonnam", "jeollanam", "jeollanamdo")),
            Map.entry(39, Set.of("제주", "제주도", "제주특별자치도", "jeju", "jejudo"))
    );

    private SearchText() { }

    static String display(String raw) {
        return raw == null ? null : raw.strip().replaceAll("\\s+", " ");
    }

    static String normalize(String raw) {
        String display = display(raw);
        return display == null ? null : display.toLowerCase(Locale.ROOT);
    }

    static String normalizeTag(String raw) {
        return TagEntity.normalize(raw);
    }

    static PlaceDtos.Region matchedRegion(List<PlaceDtos.Region> regions, String query) {
        String normalized = normalizeRegion(query);
        for (PlaceDtos.Region region : regions) {
            Set<String> aliases = REGION_ALIASES.getOrDefault(region.areaCode(), Set.of());
            if (normalizeRegion(region.nameKo()).equals(normalized)
                    || normalizeRegion(region.nameEn()).equals(normalized)
                    || aliases.stream().map(SearchText::normalizeRegion).anyMatch(normalized::equals)) {
                return region;
            }
        }
        return null;
    }

    private static String normalizeRegion(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }
}
