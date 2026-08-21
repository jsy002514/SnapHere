package com.ssafy.snaphere.domain.tag.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** post_tags 는 복합 PK 조인 테이블이라 엔티티로 두지 않고 직접 다룬다. */
@Repository
@RequiredArgsConstructor
public class PostTagRepository {

    private final JdbcTemplate jdbcTemplate;

    public void link(Long postId, List<Long> tagIds, List<String> sources) {
        for (int i = 0; i < tagIds.size(); i++) {
            jdbcTemplate.update("""
                    INSERT INTO post_tags (post_id, tag_id, source) VALUES (?, ?, ?) AS incoming
                    ON DUPLICATE KEY UPDATE source = post_tags.source
                    """, postId, tagIds.get(i), sources.get(i));
        }
    }

    public void unlinkAll(Long postId) {
        jdbcTemplate.update("DELETE FROM post_tags WHERE post_id = ?", postId);
    }

    public List<Long> findTagIdsByPostId(Long postId) {
        return jdbcTemplate.queryForList(
                "SELECT tag_id FROM post_tags WHERE post_id = ? ORDER BY tag_id", Long.class, postId);
    }

    /** 태그로 게시물 검색 — idx_post_tags_tag 를 탄다. */
    public List<Long> findPostIdsByTagName(String tagName, int limit, int offset) {
        return jdbcTemplate.queryForList("""
                SELECT pt.post_id
                FROM post_tags pt
                JOIN tags t ON t.tag_id = pt.tag_id
                WHERE t.name = ?
                ORDER BY pt.post_id DESC
                LIMIT ? OFFSET ?
                """, Long.class, tagName, limit, offset);
    }

    public long countPostsByTagName(String tagName) {
        Long n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM post_tags pt JOIN tags t ON t.tag_id = pt.tag_id WHERE t.name = ?
                """, Long.class, tagName);
        return n == null ? 0 : n;
    }
}
