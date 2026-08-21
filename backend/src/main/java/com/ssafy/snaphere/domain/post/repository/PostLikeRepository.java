package com.ssafy.snaphere.domain.post.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** post_likes 는 복합 PK 조인 테이블. 엔티티 없이 직접 다룬다. */
@Repository
@RequiredArgsConstructor
public class PostLikeRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @return true 면 새로 추가됨, false 면 이미 눌러둔 상태
     *
     * INSERT IGNORE 로 처리해 "조회 후 없으면 삽입" 의 경쟁 조건을 없앤다.
     * 따닥 두 번 눌러도 카운터가 2 올라가지 않는다.
     */
    public boolean like(Long postId, Long userId) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO post_likes (post_id, user_id) VALUES (?, ?)", postId, userId) > 0;
    }

    /** @return true 면 실제로 지워짐 */
    public boolean unlike(Long postId, Long userId) {
        return jdbcTemplate.update(
                "DELETE FROM post_likes WHERE post_id = ? AND user_id = ?", postId, userId) > 0;
    }

    public boolean exists(Long postId, Long userId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ? AND user_id = ?",
                Integer.class, postId, userId);
        return n != null && n > 0;
    }

    /** 목록 화면에서 "내가 좋아요한 글" 을 한 번에 판별한다(게시물당 1쿼리 금지). */
    public List<Long> findLikedPostIds(Long userId, List<Long> postIds) {
        if (postIds.isEmpty()) return List.of();
        String ph = String.join(",", java.util.Collections.nCopies(postIds.size(), "?"));
        Object[] args = new Object[postIds.size() + 1];
        args[0] = userId;
        for (int i = 0; i < postIds.size(); i++) args[i + 1] = postIds.get(i);
        return jdbcTemplate.queryForList(
                "SELECT post_id FROM post_likes WHERE user_id = ? AND post_id IN (" + ph + ")",
                Long.class, args);
    }

    public void deleteByPostId(Long postId) {
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", postId);
    }
}
