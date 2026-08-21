package com.ssafy.snaphere.domain.heatmap.repository;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 히트맵 격자 집계. posts 를 격자 단위로 묶어 heatmap_cells 를 다시 만든다.
 *
 * 설계 판단
 *  · UPSERT 가 아니라 "해당 (gridLevel, period) 를 전부 지우고 다시 넣는다".
 *    게시물이 사라진 격자가 남아 있으면 지도에 유령 열점이 계속 표시된다.
 *    격자 수가 수백 개 수준이라 전체 재계산 비용이 UPSERT + 정리보다 싸다.
 *  · intensity 는 두 번째 문장에서 정규화한다. 한 번의 GROUP BY 로는 최댓값을 알 수 없다.
 *  · 대표 게시물(top_post) 은 세 번째 문장에서 채운다. 썸네일을 미리 저장해야
 *    지도 드래그마다 조인이 생기지 않는다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class HeatmapAggregationRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param gridSize 격자 크기(도). 0.01 이면 약 1.1km
     * @param from     집계 시작 시각. null 이면 기간 제한 없음(ALL)
     * @return 만들어진 격자 수
     */
    public int rebuild(int gridLevel, String period, double gridSize,
                       LocalDateTime from, LocalDateTime nextRefreshAt) {

        jdbcTemplate.update("DELETE FROM heatmap_cells WHERE grid_level = ? AND period = ?",
                gridLevel, period);

        // ⚠️ POINT(경도, 위도) 순서. 격자 중심 좌표도 같은 규칙을 따른다.
        // ⚠️ 격자 좌표는 파생 테이블에서 먼저 계산한다.
        //    SELECT 목록에서 ROUND(...) 를 직접 쓰면 sql_mode=only_full_group_by 에 걸려
        //    "Expression #5 is not in GROUP BY clause" 로 실패한다 (2026-08-22 실측).
        // ⚠️ POINT(경도, 위도) 순서.
        String insert = """
                INSERT INTO heatmap_cells
                    (grid_level, period, cell_lat, cell_lng, geom, area_code,
                     post_count, visit_count, user_count, place_count,
                     intensity, last_post_at, calculated_at, next_refresh_at)
                SELECT ?, ?, t.cell_lat, t.cell_lng,
                       ST_SRID(POINT(t.cell_lng, t.cell_lat), 4326),
                       MIN(t.area_code),
                       COUNT(*),
                       0,
                       COUNT(DISTINCT t.user_id),
                       COUNT(DISTINCT t.place_id),
                       0,
                       MAX(t.created_at),
                       NOW(6),
                       ?
                FROM (
                    SELECT ROUND(p.lat / ?) * ? AS cell_lat,
                           ROUND(p.lng / ?) * ? AS cell_lng,
                           p.area_code, p.user_id, p.place_id, p.created_at
                    FROM posts p
                    WHERE p.status = 'ACTIVE'
                      AND p.has_location = 1
                      AND (? IS NULL OR p.created_at >= ?)
                ) t
                GROUP BY t.cell_lat, t.cell_lng
                """;
        java.sql.Timestamp fromTs = from == null ? null : java.sql.Timestamp.valueOf(from);
        int cells = jdbcTemplate.update(insert,
                gridLevel, period,
                java.sql.Timestamp.valueOf(nextRefreshAt),
                gridSize, gridSize, gridSize, gridSize,
                fromTs, fromTs);

        if (cells == 0) return 0;

        // intensity 정규화 — 같은 (gridLevel, period) 안에서 최대 post_count 를 1.0 으로 본다.
        jdbcTemplate.update("""
                UPDATE heatmap_cells c
                JOIN (SELECT MAX(post_count) AS mx FROM heatmap_cells
                      WHERE grid_level = ? AND period = ?) m
                SET c.intensity = LEAST(1.0, GREATEST(0.0, c.post_count / NULLIF(m.mx, 0)))
                WHERE c.grid_level = ? AND c.period = ?
                """, gridLevel, period, gridLevel, period);

        // 격자별 대표 게시물 + 대표 장소. 썸네일을 캐시해 조회 시 조인을 없앤다.
        // ROW_NUMBER() 정렬에 post_id 를 붙여 좋아요 수가 같을 때 대표가 흔들리지 않게 한다.
        jdbcTemplate.update("""
                UPDATE heatmap_cells c
                JOIN (
                    SELECT t.cell_lat, t.cell_lng, t.post_id, t.place_id, t.thumbnail_url
                    FROM (
                        SELECT ROUND(p.lat / ?) * ? AS cell_lat,
                               ROUND(p.lng / ?) * ? AS cell_lng,
                               p.post_id, p.place_id, p.thumbnail_url,
                               ROW_NUMBER() OVER (
                                   PARTITION BY ROUND(p.lat / ?) * ?, ROUND(p.lng / ?) * ?
                                   ORDER BY p.like_count DESC, p.post_id DESC
                               ) AS rn
                        FROM posts p
                        WHERE p.status = 'ACTIVE' AND p.has_location = 1
                          AND (? IS NULL OR p.created_at >= ?)
                    ) t
                    WHERE t.rn = 1
                ) top ON top.cell_lat = c.cell_lat AND top.cell_lng = c.cell_lng
                SET c.top_post_id = top.post_id,
                    c.top_place_id = top.place_id,
                    c.top_post_thumb = top.thumbnail_url
                WHERE c.grid_level = ? AND c.period = ?
                """,
                gridSize, gridSize, gridSize, gridSize,
                gridSize, gridSize, gridSize, gridSize,
                fromTs, fromTs,
                gridLevel, period);

        return cells;
    }

    /** 시도별 활동량. 메인 지도가 축소 상태일 때 격자 대신 시도 단위로 칠한다. */
    public int rebuildRegionStats() {
        // 집계 대상이 없는 지역도 행이 있어야 프론트가 17개를 모두 그릴 수 있다.
        jdbcTemplate.update("""
                INSERT INTO region_stats (area_code) SELECT r.area_code FROM regions r
                ON DUPLICATE KEY UPDATE area_code = region_stats.area_code
                """);

        jdbcTemplate.update("""
                UPDATE region_stats rs
                LEFT JOIN (
                    SELECT area_code,
                           COUNT(*) AS place_count,
                           SUM(place_type = 'USER') AS user_place_count
                    FROM places WHERE status = 'ACTIVE' GROUP BY area_code
                ) pc ON pc.area_code = rs.area_code
                SET rs.place_count = COALESCE(pc.place_count, 0),
                    rs.user_place_count = COALESCE(pc.user_place_count, 0)
                """);

        return jdbcTemplate.update("""
                UPDATE region_stats rs
                LEFT JOIN (
                    SELECT area_code,
                           COUNT(*) AS post_count,
                           COUNT(DISTINCT user_id) AS contributor_count,
                           SUM(created_at >= NOW(6) - INTERVAL 1 HOUR) AS recent_1h,
                           SUM(created_at >= NOW(6) - INTERVAL 24 HOUR) AS recent_24h,
                           MAX(created_at) AS last_post_at
                    FROM posts WHERE status = 'ACTIVE' GROUP BY area_code
                ) ps ON ps.area_code = rs.area_code
                CROSS JOIN (
                    SELECT GREATEST(1, COALESCE(MAX(c), 0)) AS mx FROM (
                        SELECT SUM(created_at >= NOW(6) - INTERVAL 1 HOUR) AS c
                        FROM posts WHERE status = 'ACTIVE' GROUP BY area_code
                    ) x
                ) m
                SET rs.post_count = COALESCE(ps.post_count, 0),
                    rs.contributor_count = COALESCE(ps.contributor_count, 0),
                    rs.recent_post_1h = COALESCE(ps.recent_1h, 0),
                    rs.recent_post_24h = COALESCE(ps.recent_24h, 0),
                    rs.last_post_at = ps.last_post_at,
                    rs.traffic_intensity = LEAST(1.0, COALESCE(ps.recent_1h, 0) / m.mx)
                """);
    }
}
