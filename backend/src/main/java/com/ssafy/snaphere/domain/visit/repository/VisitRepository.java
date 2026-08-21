package com.ssafy.snaphere.domain.visit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 방문 기록. 같은 날 같은 장소는 1회만 남는다 (uk_visits_daily).
 *
 * ⚠️ NO_LOCATION 게시물은 방문으로 인정하지 않는다. tier 컬럼에 ON_SITE 또는 LOCATION_CONFIRMED 만 들어간다.
 *    이 규칙이 무너지면 위치 없는 사진만 올려도 방문 배지가 쌓여 기능 자체가 무의미해진다.
 */
@Repository
@RequiredArgsConstructor
public class VisitRepository {

    private final JdbcTemplate jdbcTemplate;

    /** @return true 면 새 방문 기록이 생성됨. 같은 날 이미 있으면 false. */
    public boolean record(Long userId, Long placeId, int areaCode, Long postId,
                          String source, String tier, LocalDate visitedOn) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO visits
                    (user_id, place_id, area_code, post_id, source, tier, visited_on, visited_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(6))
                """, userId, placeId, areaCode, postId, source, tier, java.sql.Date.valueOf(visitedOn)) > 0;
    }

    public boolean existsToday(Long userId, Long placeId, LocalDate on) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM visits WHERE user_id = ? AND place_id = ? AND visited_on = ?
                """, Integer.class, userId, placeId, java.sql.Date.valueOf(on));
        return n != null && n > 0;
    }

    public boolean hasVisited(Long userId, Long placeId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits WHERE user_id = ? AND place_id = ?",
                Integer.class, userId, placeId);
        return n != null && n > 0;
    }

    /** 내 방문 기록 목록 — 정렬에 PK 를 붙여 페이지 경계를 고정한다. */
    public List<Map<String, Object>> findMyVisits(Long userId, int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT v.visit_id, v.place_id, v.area_code, v.post_id, v.source, v.tier,
                       v.visited_on, v.visited_at,
                       p.title AS place_title, p.first_image_thumb AS thumbnail_url
                FROM visits v
                JOIN places p ON p.place_id = v.place_id
                WHERE v.user_id = ?
                ORDER BY v.visited_at DESC, v.visit_id DESC
                LIMIT ? OFFSET ?
                """, userId, limit, offset);
    }

    public long countMyVisits(Long userId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visits WHERE user_id = ?", Long.class, userId);
        return n == null ? 0 : n;
    }

    /** 방문 통계 — 마이페이지의 "몇 개 지역, 몇 곳" */
    public Map<String, Object> stats(Long userId) {
        return jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT place_id)  AS place_count,
                       COUNT(DISTINCT area_code) AS region_count,
                       COUNT(*)                  AS visit_count,
                       SUM(tier = 'ON_SITE')     AS on_site_count,
                       MAX(visited_at)           AS last_visited_at
                FROM visits WHERE user_id = ?
                """, userId);
    }

    /** 지역별 방문 수 — 마이페이지 지도 색칠용 */
    public List<Map<String, Object>> statsByRegion(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT v.area_code, r.name_ko, COUNT(DISTINCT v.place_id) AS place_count
                FROM visits v JOIN regions r ON r.area_code = v.area_code
                WHERE v.user_id = ?
                GROUP BY v.area_code, r.name_ko
                ORDER BY place_count DESC, v.area_code ASC
                """, userId);
    }

    /** 장소 방문자 목록 */
    public List<Map<String, Object>> findVisitorsOfPlace(Long placeId, int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT u.user_id, u.nickname, u.profile_image_url, u.grade, MAX(v.visited_at) AS visited_at
                FROM visits v JOIN users u ON u.user_id = v.user_id
                WHERE v.place_id = ? AND u.status = 'ACTIVE'
                GROUP BY u.user_id, u.nickname, u.profile_image_url, u.grade
                ORDER BY visited_at DESC, u.user_id DESC
                LIMIT ? OFFSET ?
                """, placeId, limit, offset);
    }

    public void deleteByPostId(Long postId) {
        jdbcTemplate.update("DELETE FROM visits WHERE post_id = ?", postId);
    }
}
