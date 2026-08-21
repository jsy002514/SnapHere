package com.ssafy.snaphere.domain.follow.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FollowRepository {

    private final JdbcTemplate jdbcTemplate;

    /** @return true 면 새로 팔로우됨. INSERT IGNORE 로 중복 클릭을 흡수한다. */
    public boolean follow(Long followerId, Long followingId) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO follows (follower_id, following_id) VALUES (?, ?)",
                followerId, followingId) > 0;
    }

    public boolean unfollow(Long followerId, Long followingId) {
        return jdbcTemplate.update(
                "DELETE FROM follows WHERE follower_id = ? AND following_id = ?",
                followerId, followingId) > 0;
    }

    public boolean exists(Long followerId, Long followingId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE follower_id = ? AND following_id = ?",
                Integer.class, followerId, followingId);
        return n != null && n > 0;
    }

    public long countFollowingToday(Long followerId) {
        Long n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM follows
                WHERE follower_id = ? AND created_at >= CURDATE()
                """, Long.class, followerId);
        return n == null ? 0 : n;
    }

    /** 팔로워 목록 — 정렬에 PK 를 붙여 동시각 레코드의 페이지 경계를 고정한다. */
    public List<Long> findFollowerIds(Long userId, int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT follower_id FROM follows WHERE following_id = ?
                ORDER BY created_at DESC, follower_id DESC LIMIT ? OFFSET ?
                """, Long.class, userId, limit, offset);
    }

    public List<Long> findFollowingIds(Long userId, int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT following_id FROM follows WHERE follower_id = ?
                ORDER BY created_at DESC, following_id DESC LIMIT ? OFFSET ?
                """, Long.class, userId, limit, offset);
    }

    public List<Long> findAllFollowingIds(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT following_id FROM follows WHERE follower_id = ?", Long.class, userId);
    }

    public long countFollowers(Long userId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE following_id = ?", Long.class, userId);
        return n == null ? 0 : n;
    }

    public long countFollowings(Long userId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE follower_id = ?", Long.class, userId);
        return n == null ? 0 : n;
    }

    /** 목록에서 "내가 팔로우 중인지" 를 한 번에 판별한다. */
    public List<Long> findFollowedAmong(Long followerId, List<Long> candidateIds) {
        if (candidateIds.isEmpty()) return List.of();
        String ph = String.join(",", java.util.Collections.nCopies(candidateIds.size(), "?"));
        Object[] args = new Object[candidateIds.size() + 1];
        args[0] = followerId;
        for (int i = 0; i < candidateIds.size(); i++) args[i + 1] = candidateIds.get(i);
        return jdbcTemplate.queryForList(
                "SELECT following_id FROM follows WHERE follower_id = ? AND following_id IN (" + ph + ")",
                Long.class, args);
    }

    /**
     * 추천 사용자 — "내가 팔로우한 사람이 팔로우하는 사람" 중 아직 내가 안 따라간 사람.
     * 데이터가 없을 때를 대비해 인기 사용자로 채우는 것은 서비스에서 처리한다.
     */
    public List<Long> findSuggestions(Long userId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT f2.following_id
                FROM follows f1
                JOIN follows f2 ON f2.follower_id = f1.following_id
                WHERE f1.follower_id = ?
                  AND f2.following_id <> ?
                  AND f2.following_id NOT IN (SELECT following_id FROM follows WHERE follower_id = ?)
                GROUP BY f2.following_id
                ORDER BY COUNT(*) DESC, f2.following_id ASC
                LIMIT ?
                """, Long.class, userId, userId, userId, limit);
    }
}
