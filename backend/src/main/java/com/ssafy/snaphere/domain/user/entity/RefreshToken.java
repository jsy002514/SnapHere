package com.ssafy.snaphere.domain.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 리프레시 토큰. 원문은 저장하지 않고 SHA-256 해시만 보관한다. */
@Getter
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by", length = 64)
    private String replacedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private RefreshToken(Long userId, String tokenHash, String deviceId, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }

    /** 로테이션: 쓰면 폐기하고 다음 토큰 해시를 기록한다. */
    public void rotate(String nextTokenHash) {
        this.revokedAt = LocalDateTime.now();
        this.replacedBy = nextTokenHash;
    }

    public void revoke() {
        if (this.revokedAt == null) this.revokedAt = LocalDateTime.now();
    }
}
