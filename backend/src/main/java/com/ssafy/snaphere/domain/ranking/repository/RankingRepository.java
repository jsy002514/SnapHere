package com.ssafy.snaphere.domain.ranking.repository;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 랭킹 집계·조회. 전부 SQL 로 처리한다.
 *
 * ⚠️ 점수 공식의 핵심은 Tier 가중치다. NO_LOCATION 게시물은 0점이라 랭킹에 기여하지 않는다.
 *    이 규칙이 없으면 아무 사진이나 대량 업로드해서 랭킹을 조작할 수 있다.
 *    가중치 수치는 아직 팀 미결정 사항이며(작업현황 문서 미결정 5번) application.yml 로 뺄 수 있게 파라미터화했다.
 */
@Repository
@RequiredArgsConstructor
public class RankingRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 랭킹 재계산. area_code = 0 은 전국을 뜻한다.
     *
     * ⚠️ 반드시 트랜잭션 안에서 호출해야 한다. 임시 테이블은 커넥션 단위라
     *    커넥션 풀에서 다른 커넥션을 잡으면 "table doesn't exist" 가 난다.
     *
     * 왜 임시 테이블인가
     *   `INSERT ... SELECT ... AS alias ON DUPLICATE KEY UPDATE` 는 MySQL 문법 오류다
     *   (행 별칭은 VALUES(...) 형태에서만 쓸 수 있다 — 2026-08-22 실측 확인).
     *   deprecated 된 VALUES() 함수를 쓰지 않고, 순위 변동(previous_rank)까지 보존하려면
     *   "새 순위를 임시 테이블에 만들고 → 기존 rank_no 를 previous_rank 로 밀고 → 갱신·삽입·정리" 4단계가 가장 명확하다.
     *
     * @param days null 이면 ALL_TIME
     * @return 랭킹에 오른 장소 수
     */
    public int rebuild(int areaCode, String period, String theme, Integer days,
                       double onSiteWeight, double confirmedWeight,
                       double likeWeight, double commentWeight, int limit) {

        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS tmp_rank");
        jdbcTemplate.execute("""
                CREATE TEMPORARY TABLE tmp_rank (
                    place_id   BIGINT        PRIMARY KEY,
                    rank_no    INT           NOT NULL,
                    score      DECIMAL(12,2) NOT NULL,
                    post_count INT           NOT NULL,
                    like_count INT           NOT NULL
                )""");

        int ranked = jdbcTemplate.update("""
                INSERT INTO tmp_rank (place_id, rank_no, score, post_count, like_count)
                SELECT t.place_id,
                       ROW_NUMBER() OVER (ORDER BY t.score DESC, t.place_id ASC),
                       t.score, t.post_count, t.like_count
                FROM (
                    SELECT p.place_id,
                           COUNT(*)                       AS post_count,
                           COALESCE(SUM(p.like_count), 0)  AS like_count,
                           ROUND(
                               SUM(CASE p.tier
                                       WHEN 'ON_SITE'            THEN ?
                                       WHEN 'LOCATION_CONFIRMED' THEN ?
                                       ELSE 0 END)
                             + SUM(p.like_count)    * ?
                             + SUM(p.comment_count) * ?
                           , 2) AS score
                    FROM posts p
                    WHERE p.status = 'ACTIVE'
                      AND p.place_id IS NOT NULL
                      AND p.tier <> 'NO_LOCATION'
                      AND (? = 0 OR p.area_code = ?)
                      AND (? IS NULL OR p.created_at >= NOW(6) - INTERVAL ? DAY)
                    GROUP BY p.place_id
                    HAVING score > 0
                ) t
                ORDER BY t.score DESC, t.place_id ASC
                LIMIT ?
                """,
                onSiteWeight, confirmedWeight, likeWeight, commentWeight,
                areaCode, areaCode, days, days, limit);

        // 1) 현재 순위를 previous_rank 로 밀어 "순위 변동" 화살표의 근거를 남긴다
        jdbcTemplate.update("""
                UPDATE place_rankings SET previous_rank = rank_no
                WHERE area_code = ? AND period = ? AND theme = ?
                """, areaCode, period, theme);

        // 2) 기존 슬롯 갱신
        jdbcTemplate.update("""
                UPDATE place_rankings r JOIN tmp_rank t ON t.place_id = r.place_id
                SET r.rank_no = t.rank_no, r.score = t.score,
                    r.post_count = t.post_count, r.like_count = t.like_count,
                    r.calculated_at = NOW(6)
                WHERE r.area_code = ? AND r.period = ? AND r.theme = ?
                """, areaCode, period, theme);

        // 3) 신규 진입 (previous_rank = NULL → 프론트가 NEW 배지를 띄운다)
        jdbcTemplate.update("""
                INSERT INTO place_rankings
                    (area_code, period, theme, place_id, rank_no, previous_rank,
                     score, post_count, like_count, visit_count, calculated_at)
                SELECT ?, ?, ?, t.place_id, t.rank_no, NULL,
                       t.score, t.post_count, t.like_count, 0, NOW(6)
                FROM tmp_rank t
                LEFT JOIN place_rankings r
                       ON r.place_id = t.place_id AND r.area_code = ? AND r.period = ? AND r.theme = ?
                WHERE r.ranking_id IS NULL
                """, areaCode, period, theme, areaCode, period, theme);

        // 4) 탈락 정리 — 남겨두면 예전 순위가 계속 노출된다
        jdbcTemplate.update("""
                DELETE r FROM place_rankings r
                LEFT JOIN tmp_rank t ON t.place_id = r.place_id
                WHERE r.area_code = ? AND r.period = ? AND r.theme = ? AND t.place_id IS NULL
                """, areaCode, period, theme);

        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS tmp_rank");
        return ranked;
    }

    /** 순위에서 빠진 장소를 정리한다. 남겨두면 예전 순위가 계속 노출된다. */
    public int pruneStale(int areaCode, String period, String theme, int limit) {
        return jdbcTemplate.update("""
                DELETE FROM place_rankings
                WHERE area_code = ? AND period = ? AND theme = ? AND rank_no > ?
                """, areaCode, period, theme, limit);
    }

    /**
     * 순위 변동 그래프용 일별 스냅샷. 90일 후 정리 배치가 지운다.
     *
     * UPSERT 대신 "오늘 것 지우고 다시 넣기" 로 처리한다.
     * INSERT ... SELECT 에는 행 별칭을 쓸 수 없고, 같은 날 재실행 시 값을 그대로 덮어쓰는 게 의도이기 때문이다.
     */
    public int snapshot(int areaCode, String period, String theme) {
        jdbcTemplate.update("""
                DELETE FROM ranking_history
                WHERE snapshot_date = CURDATE() AND area_code = ? AND period = ? AND theme = ?
                """, areaCode, period, theme);
        return jdbcTemplate.update("""
                INSERT INTO ranking_history
                    (area_code, period, theme, place_id, rank_no, score, snapshot_date)
                SELECT area_code, period, theme, place_id, rank_no, score, CURDATE()
                FROM place_rankings
                WHERE area_code = ? AND period = ? AND theme = ?
                """, areaCode, period, theme);
    }

    /** 90일 지난 스냅샷 정리. 안 지우면 계속 쌓인다. */
    public int pruneOldHistory(int keepDays) {
        return jdbcTemplate.update(
                "DELETE FROM ranking_history WHERE snapshot_date < CURDATE() - INTERVAL ? DAY", keepDays);
    }

    public List<Map<String, Object>> findRanking(int areaCode, String period, String theme, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT r.rank_no, r.previous_rank, r.score, r.post_count, r.like_count,
                       p.place_id, p.title, p.addr1, p.first_image_thumb AS thumbnail_url,
                       p.area_code, p.content_type_id, p.lat, p.lng,
                       r.calculated_at
                FROM place_rankings r
                JOIN places p ON p.place_id = r.place_id
                WHERE r.area_code = ? AND r.period = ? AND r.theme = ? AND p.status = 'ACTIVE'
                ORDER BY r.rank_no ASC
                LIMIT ?
                """, areaCode, period, theme, limit);
    }

    /** 장소 상세의 "지역 N위 / 전국 M위" */
    public Map<String, Object> findRankOf(Long placeId, String period, String theme) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT area_code, rank_no FROM place_rankings
                WHERE place_id = ? AND period = ? AND theme = ?
                """, placeId, period, theme);
        Integer regionRank = null, nationalRank = null;
        for (Map<String, Object> r : rows) {
            int area = ((Number) r.get("area_code")).intValue();
            int rank = ((Number) r.get("rank_no")).intValue();
            if (area == 0) nationalRank = rank; else regionRank = rank;
        }
        return Map.of("regionRank", regionRank == null ? -1 : regionRank,
                      "nationalRank", nationalRank == null ? -1 : nationalRank,
                      "period", period);
    }

    /** 추천 장소 — 랭킹이 비어 있을 때의 fallback. 데이터가 없어도 화면이 비지 않게 한다. */
    public List<Map<String, Object>> findFallbackRecommendations(int areaCode, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT p.place_id, p.title, p.first_image_thumb AS thumbnail_url,
                       p.post_count AS recent_post_count,
                       CASE WHEN p.is_featured = 1 THEN 'EDITOR_PICK'
                            WHEN p.post_count = 0  THEN 'HIDDEN_GEM'
                            ELSE 'TOP_RATED' END AS reason
                FROM places p
                WHERE p.status = 'ACTIVE' AND (? = 0 OR p.area_code = ?)
                ORDER BY p.is_featured DESC, p.post_count DESC, p.place_id ASC
                LIMIT ?
                """, areaCode, areaCode, limit);
    }

    /** 지역별 인기 태그 재집계 */
    public int rebuildRegionTagStats() {
        jdbcTemplate.update("DELETE FROM region_tag_stats");
        return jdbcTemplate.update("""
                INSERT INTO region_tag_stats (area_code, tag_id, post_count, rank_no)
                SELECT t.area_code, t.tag_id, t.post_count,
                       ROW_NUMBER() OVER (PARTITION BY t.area_code ORDER BY t.post_count DESC, t.tag_id ASC)
                FROM (
                    SELECT p.area_code, pt.tag_id, COUNT(*) AS post_count
                    FROM post_tags pt
                    JOIN posts p ON p.post_id = pt.post_id
                    WHERE p.status = 'ACTIVE'
                    GROUP BY p.area_code, pt.tag_id
                ) t
                """);
    }

    public List<Map<String, Object>> findPopularTags(int areaCode, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT s.tag_id, tg.name, s.post_count, s.rank_no
                FROM region_tag_stats s JOIN tags tg ON tg.tag_id = s.tag_id
                WHERE s.area_code = ?
                ORDER BY s.rank_no ASC
                LIMIT ?
                """, areaCode, limit);
    }
}
