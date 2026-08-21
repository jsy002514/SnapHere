package com.ssafy.snaphere.domain.search.service;

import com.ssafy.snaphere.domain.place.dto.PlaceDtos.NearbyItem;
import com.ssafy.snaphere.domain.place.service.PlaceService;
import com.ssafy.snaphere.domain.post.entity.PostStatus;
import com.ssafy.snaphere.domain.post.repository.PostRepository;
import com.ssafy.snaphere.domain.tag.repository.TagRepository;
import com.ssafy.snaphere.global.util.FulltextQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 검색.
 *
 * ⚠️ TourAPI 의 searchKeyword 를 런타임에 호출하지 않는다.
 *    ① 응답이 느리다 ② 호출 한도가 순식간에 소진된다 ③ 외부 장애가 곧 우리 장애가 된다
 *    ④ 사용자가 만든 장소(USER)는 TourAPI 에 없다 — 우리 DB 로만 검색해야 둘 다 나온다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final PlaceService placeService;
    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public SearchResponse search(String keyword, Integer areaCode, Long userId, int limit) {
        List<NearbyItem> places = placeService.search(keyword, areaCode, limit);

        String booleanQuery = FulltextQuery.toBooleanMode(keyword);
        List<Long> postIds = booleanQuery == null ? List.of()
                : postRepository.searchIds(booleanQuery, areaCode, limit);

        // Optional.map 안에서 Map.of 를 조립하면 제네릭 추론이 애매해진다.
        // 명시적으로 만들어 담는다 — 읽기도 쉽고 컴파일러가 헷갈릴 여지가 없다.
        List<Map<String, Object>> tags = new ArrayList<>();
        tagRepository.findByName(normalizeTag(keyword)).ifPresent(t -> {
            Map<String, Object> tag = new LinkedHashMap<>();
            tag.put("tagId", t.getId());
            tag.put("name", t.getName());
            tag.put("postCount", t.getUsageCount());
            tags.add(tag);
        });

        int total = places.size() + postIds.size();
        logSearch(userId, keyword, total);

        return new SearchResponse(keyword, places, postIds, tags, total);
    }

    /** 인기 검색어 — 최근 7일 집계. 검색 로그가 없으면 빈 목록이다(가짜 데이터를 넣지 않는다). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> popularKeywords(int limit) {
        return jdbcTemplate.queryForList("""
                SELECT keyword, COUNT(*) AS search_count
                FROM search_logs
                WHERE created_at >= NOW(6) - INTERVAL 7 DAY
                GROUP BY keyword
                ORDER BY search_count DESC, keyword ASC
                LIMIT ?
                """, limit);
    }

    /**
     * 검색어 기록. 실패해도 검색 결과를 되돌리지 않는다(부가 기능이다).
     * 결과가 0건인 검색어도 남긴다 — "찾는데 없는 것" 이 콘텐츠 보강의 힌트가 된다.
     */
    private void logSearch(Long userId, String keyword, int resultCount) {
        try {
            String k = keyword == null ? "" : keyword.trim();
            if (k.isEmpty() || k.length() > 100) return;
            jdbcTemplate.update(
                    "INSERT INTO search_logs (user_id, keyword, result_count) VALUES (?, ?, ?)",
                    userId, k, resultCount);
        } catch (Exception e) {
            log.debug("검색 로그 적재 실패(무시): {}", e.getMessage());
        }
    }

    private static String normalizeTag(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("[\\s#,]+", "").toLowerCase();
    }

    public record SearchResponse(String keyword,
                                 List<NearbyItem> places,
                                 List<Long> postIds,
                                 List<Map<String, Object>> tags,
                                 int totalCount) {}
}
