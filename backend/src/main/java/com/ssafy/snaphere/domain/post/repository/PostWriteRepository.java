package com.ssafy.snaphere.domain.post.repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * geom(POINT SRID 4326) 과 content(TEXT) 를 쓰는 INSERT/UPDATE 는 여기에만 둔다.
 *
 * ⚠️ POINT(경도, 위도) 순서. 위치가 없으면 POINT(0,0) + has_location = 0.
 *    geom 이 NOT NULL 이라 위치 없는 글도 값을 넣어야 한다.
 * ⚠️ tier 는 반드시 서버가 판정한 값을 받는다. 이 메서드에 클라이언트 값을 넘기지 말 것.
 */
@Repository
@RequiredArgsConstructor
public class PostWriteRepository {

    private final JdbcTemplate jdbcTemplate;

    public Long insert(Long userId, Long placeId, int areaCode, String category,
                       String title, String content,
                       int mediaCount, int videoCount, String thumbnailUrl, BigDecimal thumbnailRatio,
                       Double lat, Double lng, Integer distanceM,
                       String source, String tier, LocalDateTime takenAt) {

        boolean hasLocation = lat != null && lng != null;
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO posts
                        (user_id, place_id, area_code, category, title, content,
                         media_count, video_count, thumbnail_url, thumbnail_ratio,
                         has_location, lat, lng, geom, distance_m,
                         source, tier, taken_at, status)
                    VALUES (?, ?, ?, ?, ?, ?,
                            ?, ?, ?, ?,
                            ?, ?, ?, ST_SRID(POINT(?, ?), 4326), ?,
                            ?, ?, ?, 'ACTIVE')
                    """, new String[]{"post_id"});
            int i = 1;
            ps.setLong(i++, userId);
            if (placeId == null) ps.setNull(i++, Types.BIGINT); else ps.setLong(i++, placeId);
            ps.setInt(i++, areaCode);
            ps.setString(i++, category);
            if (title == null) ps.setNull(i++, Types.VARCHAR); else ps.setString(i++, title);
            if (content == null) ps.setNull(i++, Types.LONGVARCHAR); else ps.setString(i++, content);
            ps.setInt(i++, mediaCount);
            ps.setInt(i++, videoCount);
            if (thumbnailUrl == null) ps.setNull(i++, Types.VARCHAR); else ps.setString(i++, thumbnailUrl);
            if (thumbnailRatio == null) ps.setNull(i++, Types.DECIMAL); else ps.setBigDecimal(i++, thumbnailRatio);
            ps.setInt(i++, hasLocation ? 1 : 0);
            if (lat == null) ps.setNull(i++, Types.DECIMAL); else ps.setBigDecimal(i++, BigDecimal.valueOf(lat));
            if (lng == null) ps.setNull(i++, Types.DECIMAL); else ps.setBigDecimal(i++, BigDecimal.valueOf(lng));
            ps.setDouble(i++, hasLocation ? lng : 0d);   // ⚠️ 경도 먼저
            ps.setDouble(i++, hasLocation ? lat : 0d);
            if (distanceM == null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, distanceM);
            ps.setString(i++, source);
            ps.setString(i++, tier);
            if (takenAt == null) ps.setNull(i, Types.TIMESTAMP);
            else ps.setTimestamp(i, java.sql.Timestamp.valueOf(takenAt));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void updateContent(Long postId, String content) {
        jdbcTemplate.update("UPDATE posts SET content = ? WHERE post_id = ?", content, postId);
    }

    /** 카운터 증감. 음수로 내려가지 않게 막는다. 최종 정합성은 새벽 보정 배치가 맞춘다. */
    public void addLikeCount(Long postId, int delta) {
        jdbcTemplate.update("UPDATE posts SET like_count = GREATEST(0, like_count + ?) WHERE post_id = ?",
                delta, postId);
    }

    public void addCommentCount(Long postId, int delta) {
        jdbcTemplate.update("UPDATE posts SET comment_count = GREATEST(0, comment_count + ?) WHERE post_id = ?",
                delta, postId);
    }

    public void addBookmarkCount(Long postId, int delta) {
        jdbcTemplate.update("UPDATE posts SET bookmark_count = GREATEST(0, bookmark_count + ?) WHERE post_id = ?",
                delta, postId);
    }

    public void increaseViewCount(Long postId) {
        jdbcTemplate.update("UPDATE posts SET view_count = view_count + 1 WHERE post_id = ?", postId);
    }

    public void addReportCount(Long postId, int delta) {
        jdbcTemplate.update("UPDATE posts SET report_count = GREATEST(0, report_count + ?) WHERE post_id = ?",
                delta, postId);
    }
}
