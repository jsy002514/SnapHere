package com.ssafy.snaphere.domain.place.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * geom(POINT SRID 4326) 을 쓰는 INSERT 는 여기에만 둔다.
 *
 * ⚠️ POINT(경도, 위도) 순서. JPA 로 좌표를 쓰지 않는 이유가 이 한 줄 때문이다.
 *    엔티티 필드로 두면 누군가 lat/lng 순서를 바꿔 넣어도 컴파일이 통과한다.
 */
@Repository
@RequiredArgsConstructor
public class PlaceWriteRepository {

    private final JdbcTemplate jdbcTemplate;

    /** @return 생성된 place_id */
    public Long insertUserPlace(Long userId, String title, double lat, double lng,
                                String addr1, int areaCode, int verifyRadiusM) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO places
                        (place_type, created_by_user_id, title, addr1, area_code,
                         lat, lng, geom, has_coordinate, verify_radius_m, status)
                    VALUES ('USER', ?, ?, ?, ?, ?, ?, ST_SRID(POINT(?, ?), 4326), 1, ?, 'ACTIVE')
                    """, new String[]{"place_id"});
            int i = 1;
            ps.setLong(i++, userId);
            ps.setString(i++, title);
            if (addr1 == null) ps.setNull(i++, java.sql.Types.VARCHAR); else ps.setString(i++, addr1);
            ps.setInt(i++, areaCode);
            ps.setBigDecimal(i++, java.math.BigDecimal.valueOf(lat));
            ps.setBigDecimal(i++, java.math.BigDecimal.valueOf(lng));
            ps.setDouble(i++, lng);   // ⚠️ 경도 먼저
            ps.setDouble(i++, lat);
            ps.setInt(i, verifyRadiusM);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    /**
     * 좌표로 area_code 를 추정한다.
     * 가장 가까운 기존 장소(20km 안)의 area_code 를 쓴다 — 시도 중심점과의 거리보다 훨씬 정확하다.
     * @return 없으면 null (호출자가 시도 중심점 기준으로 대체)
     */
    public Integer resolveAreaCodeByNearestPlace(double lat, double lng) {
        var rows = jdbcTemplate.queryForList("""
                SELECT p.area_code
                FROM places p
                WHERE p.status = 'ACTIVE' AND p.has_coordinate = 1
                  AND MBRContains(ST_Buffer(ST_SRID(POINT(?, ?), 4326), 20000), p.geom)
                ORDER BY ST_Distance_Sphere(p.geom, ST_SRID(POINT(?, ?), 4326)) ASC, p.place_id ASC
                LIMIT 1
                """, Integer.class, lng, lat, lng, lat);
        return rows.isEmpty() ? null : (Integer) rows.get(0);
    }

    /** 장소 카운터 증감. 새벽 보정 배치가 최종 정합성을 맞춘다. */
    public void addPostCount(Long placeId, int delta) {
        jdbcTemplate.update("UPDATE places SET post_count = GREATEST(0, post_count + ?) WHERE place_id = ?",
                delta, placeId);
    }

    public void addVisitCount(Long placeId, int delta) {
        jdbcTemplate.update("UPDATE places SET visit_count = GREATEST(0, visit_count + ?) WHERE place_id = ?",
                delta, placeId);
    }

    public void addLikeCount(Long placeId, int delta) {
        jdbcTemplate.update("UPDATE places SET like_count = GREATEST(0, like_count + ?) WHERE place_id = ?",
                delta, placeId);
    }

    public void addBookmarkCount(Long placeId, int delta) {
        jdbcTemplate.update("UPDATE places SET bookmark_count = GREATEST(0, bookmark_count + ?) WHERE place_id = ?",
                delta, placeId);
    }

    public void increaseViewCount(Long placeId) {
        jdbcTemplate.update("UPDATE places SET view_count = view_count + 1 WHERE place_id = ?", placeId);
    }
}
