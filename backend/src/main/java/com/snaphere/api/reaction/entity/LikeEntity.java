package com.snaphere.api.reaction.entity;

import com.snaphere.api.reaction.LikeTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 좋아요 한 건. (PST-040) */
@Entity
@Table(name = "likes")
public class LikeEntity {

    @EmbeddedId
    private LikeId id;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LikeEntity() {
    }

    public static LikeEntity of(UUID userId, LikeTargetType targetType, Long targetId) {
        LikeEntity like = new LikeEntity();
        like.id = new LikeId(userId, targetType, targetId);
        like.createdAt = OffsetDateTime.now();
        return like;
    }

    public LikeId getId() {
        return id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
