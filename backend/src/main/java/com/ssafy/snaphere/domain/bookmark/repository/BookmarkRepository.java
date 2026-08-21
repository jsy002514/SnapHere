package com.ssafy.snaphere.domain.bookmark.repository;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 저장(북마크). POST 와 PLACE 를 같은 테이블에 담는 폴리모픽 구조라 FK 를 걸 수 없다.
 * 대상 존재 여부는 서비스에서 검증한다.
 */
@Repository
@RequiredArgsConstructor
public class BookmarkRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean add(Long userId, String targetType, Long targetId) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO bookmarks (user_id, target_type, target_id) VALUES (?, ?, ?)",
                userId, targetType, targetId) > 0;
    }

    public boolean remove(Long userId, String targetType, Long targetId) {
        return jdbcTemplate.update(
                "DELETE FROM bookmarks WHERE user_id = ? AND target_type = ? AND target_id = ?",
                userId, targetType, targetId) > 0;
    }

    public boolean exists(Long userId, String targetType, Long targetId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bookmarks WHERE user_id = ? AND target_type = ? AND target_id = ?
                """, Integer.class, userId, targetType, targetId);
        return n != null && n > 0;
    }

    public List<Long> findTargetIds(Long userId, String targetType, int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT target_id FROM bookmarks
                WHERE user_id = ? AND target_type = ?
                ORDER BY created_at DESC, target_id DESC
                LIMIT ? OFFSET ?
                """, Long.class, userId, targetType, limit, offset);
    }

    public long count(Long userId, String targetType) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookmarks WHERE user_id = ? AND target_type = ?",
                Long.class, userId, targetType);
        return n == null ? 0 : n;
    }

    /** 목록에서 "내가 저장했는지" 를 한 번에 판별한다. */
    public List<Long> findBookmarkedAmong(Long userId, String targetType, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        String ph = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = new Object[ids.size() + 2];
        args[0] = userId;
        args[1] = targetType;
        for (int i = 0; i < ids.size(); i++) args[i + 2] = ids.get(i);
        return jdbcTemplate.queryForList(
                "SELECT target_id FROM bookmarks WHERE user_id = ? AND target_type = ? AND target_id IN (" + ph + ")",
                Long.class, args);
    }

    /** 저장한 장소 목록 (썸네일까지 한 번에) */
    public List<Map<String, Object>> findBookmarkedPlaces(Long userId, int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT p.place_id, p.title, p.addr1, p.first_image_thumb AS thumbnail_url,
                       p.area_code, p.post_count, b.created_at
                FROM bookmarks b JOIN places p ON p.place_id = b.target_id
                WHERE b.user_id = ? AND b.target_type = 'PLACE' AND p.status = 'ACTIVE'
                ORDER BY b.created_at DESC, p.place_id DESC
                LIMIT ? OFFSET ?
                """, userId, limit, offset);
    }
}
