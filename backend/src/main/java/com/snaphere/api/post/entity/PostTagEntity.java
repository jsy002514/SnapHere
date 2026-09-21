package com.snaphere.api.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 게시글-태그 연결. 게시글당 최소 1개, 최대 10개. (PST-004)
 *
 * <p>게시글을 수정하면 이 연결을 전부 지우고 다시 넣는다. 차이 계산 방식은 버그를 부른다 (CMU-032).
 */
@Entity
@Table(name = "post_tags")
public class PostTagEntity {

    /** 게시글당 태그 개수. 장소 태그 자동 생성으로 최소 조건을 채운다. (PST-004, CMU-026) */
    public static final int MIN_PER_POST = 1;
    public static final int MAX_PER_POST = 10;

    @EmbeddedId
    private PostTagId id;

    /** 행사 고정 태그. 사용자가 뗄 수 없다. (EVT-018) */
    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    /** 추천을 채택한 태그. 추천 로직 개선 지표로 쓴다. (CMU-029) */
    @Column(name = "is_suggested", nullable = false)
    private boolean suggested;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PostTagEntity() {
    }

    public static PostTagEntity of(Long postId, Long tagId, boolean locked, boolean suggested) {
        PostTagEntity link = new PostTagEntity();
        link.id = new PostTagId(postId, tagId);
        link.locked = locked;
        link.suggested = suggested;
        link.createdAt = OffsetDateTime.now();
        return link;
    }

    public PostTagId getId() {
        return id;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isSuggested() {
        return suggested;
    }
}
