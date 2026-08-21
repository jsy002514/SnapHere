package com.ssafy.snaphere.domain.follow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 복합 PK. JPQL 서브쿼리에서 쓰기 위해 엔티티로 둔다. */
@Getter
@Entity
@Table(name = "follows")
@IdClass(Follow.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow {

    @Id @Column(name = "follower_id")  private Long followerId;
    @Id @Column(name = "following_id") private Long followingId;

    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;

    public record Key(Long followerId, Long followingId) implements java.io.Serializable {
        public Key() { this(null, null); }
    }
}
