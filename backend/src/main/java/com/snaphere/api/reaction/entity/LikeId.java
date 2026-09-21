package com.snaphere.api.reaction.entity;

import com.snaphere.api.reaction.LikeTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code likes} 복합 키.
 *
 * <p>이 키 자체가 "사용자당 1회" 규칙이다 (PST-040). 애플리케이션에서 조회 후 삽입하면
 * 같은 사용자가 두 번 빠르게 누를 때 두 행이 들어간다.
 */
@Embeddable
public class LikeId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private LikeTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    protected LikeId() {
    }

    public LikeId(UUID userId, LikeTargetType targetType, Long targetId) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LikeTargetType getTargetType() {
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
        if (!(o instanceof LikeId other)) {
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
