package com.ssafy.snaphere.domain.comment.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CommentLikeRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean like(Long commentId, Long userId) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO comment_likes (comment_id, user_id) VALUES (?, ?)",
                commentId, userId) > 0;
    }

    public boolean unlike(Long commentId, Long userId) {
        return jdbcTemplate.update(
                "DELETE FROM comment_likes WHERE comment_id = ? AND user_id = ?",
                commentId, userId) > 0;
    }

    /** 목록에서 "내가 좋아요한 댓글" 을 한 번에 판별한다. */
    public List<Long> findLikedAmong(Long userId, List<Long> commentIds) {
        if (commentIds.isEmpty()) return List.of();
        String ph = String.join(",", java.util.Collections.nCopies(commentIds.size(), "?"));
        Object[] args = new Object[commentIds.size() + 1];
        args[0] = userId;
        for (int i = 0; i < commentIds.size(); i++) args[i + 1] = commentIds.get(i);
        return jdbcTemplate.queryForList(
                "SELECT comment_id FROM comment_likes WHERE user_id = ? AND comment_id IN (" + ph + ")",
                Long.class, args);
    }

    public void addLikeCount(Long commentId, int delta) {
        jdbcTemplate.update(
                "UPDATE comments SET like_count = GREATEST(0, like_count + ?) WHERE comment_id = ?",
                delta, commentId);
    }
}
