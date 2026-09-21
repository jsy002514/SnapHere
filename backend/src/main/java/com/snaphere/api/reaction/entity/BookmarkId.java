package com.snaphere.api.reaction.entity;

import com.snaphere.api.reaction.BookmarkTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** {@code bookmarks} 복합 키. 이 키가 중복 저장을 막는다. */
@Embeddable
public class BookmarkId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private BookmarkTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    protected BookmarkId() {
    }

    public BookmarkId(UUID userId, BookmarkTargetType targetType, Long targetId) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BookmarkTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookmarkId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId)
                && targetType == other.targetType
                && Objects.equals(targetId, other.targetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, targetType, targetId);
    }
}
