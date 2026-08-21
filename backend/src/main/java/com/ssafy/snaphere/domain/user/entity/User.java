package com.ssafy.snaphere.domain.user.entity;

import com.ssafy.snaphere.global.common.BaseTimeEntity;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import com.ssafy.snaphere.global.security.JwtTokenProvider;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 30)
    private AuthType authType;

    @Column(name = "login_id", length = 30)
    private String loginId;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(length = 30)
    private String provider;                 // 'GOOGLE' 또는 null

    @Column(name = "provider_user_id", length = 191)
    private String providerUserId;

    private String email;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(length = 150)
    private String bio;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 30)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    @Column(name = "upload_blocked_until")
    private LocalDateTime uploadBlockedUntil;

    // 알림 설정
    @Column(name = "push_like_enabled", nullable = false)    private boolean pushLikeEnabled;
    @Column(name = "push_comment_enabled", nullable = false) private boolean pushCommentEnabled;
    @Column(name = "push_follow_enabled", nullable = false)  private boolean pushFollowEnabled;
    @Column(name = "push_post_enabled", nullable = false)    private boolean pushPostEnabled;

    // 비정규화 카운터 — 매일 새벽 보정 배치 필수
    @Column(name = "follower_count", nullable = false)  private int followerCount;
    @Column(name = "following_count", nullable = false) private int followingCount;
    @Column(name = "post_count", nullable = false)      private int postCount;
    @Column(name = "visit_count", nullable = false)     private int visitCount;

    @Column(name = "popularity_score", nullable = false)
    private int popularityScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Grade grade;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sns_links")
    private Map<String, String> snsLinks;

    // 계정 삭제
    @Column(name = "withdraw_reason", length = 50)  private String withdrawReason;
    @Column(name = "withdrawn_at")                  private LocalDateTime withdrawnAt;
    @Column(name = "purge_scheduled_at")            private LocalDateTime purgeScheduledAt;
    @Column(name = "restore_key", length = 64)      private String restoreKey;

    @Builder
    private User(AuthType authType, String loginId, String passwordHash, String provider,
                 String providerUserId, String email, String nickname, String profileImageUrl,
                 String locale) {
        this.authType = authType;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.locale = locale == null ? "ko" : locale;
        this.role = "USER";
        this.status = UserStatus.ACTIVE;
        this.grade = Grade.SEED;
        this.pushLikeEnabled = true;
        this.pushCommentEnabled = true;
        this.pushFollowEnabled = true;
        this.pushPostEnabled = true;
    }

    // ── 상태 변경 (의도가 드러나는 메서드로만. @Setter 금지) ────────

    public void agreeTerms() { this.termsAgreedAt = LocalDateTime.now(); }

    public boolean hasAgreedTerms() { return termsAgreedAt != null; }

    public boolean isUploadBlocked() {
        return uploadBlockedUntil != null && uploadBlockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isActive() { return status == UserStatus.ACTIVE; }

    public boolean isWithdrawn() { return status == UserStatus.WITHDRAWN; }

    public void updateProfile(String nickname, String bio, String profileImageUrl,
                              String locale, Map<String, String> snsLinks) {
        if (nickname != null)        this.nickname = nickname;
        if (bio != null)             this.bio = bio;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
        if (locale != null)          this.locale = locale;
        if (snsLinks != null)        this.snsLinks = snsLinks;
    }

    public void changePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
    }

    public void updateNotificationSettings(Boolean like, Boolean comment, Boolean follow, Boolean post) {
        if (like != null)    this.pushLikeEnabled = like;
        if (comment != null) this.pushCommentEnabled = comment;
        if (follow != null)  this.pushFollowEnabled = follow;
        if (post != null)    this.pushPostEnabled = post;
    }

    public void applyPopularity(int score) {
        this.popularityScore = score;
        this.grade = Grade.of(score);
    }

    /**
     * 계정 삭제.
     * 원칙 1) 개인 식별 정보는 **즉시** 제거한다. 30일 유예는 계정 레코드·콘텐츠에 대한 것이다.
     * 원칙 2) 계정 행은 WITHDRAWN 으로 남기고 파기 배치가 purgeScheduledAt 이후에 지운다.
     *         (오조작 복구 / 제재 회피 방지 / 랭킹 정합성 / 법적 대응)
     */
    public void withdraw(String reason, int graceDays) {
        if (isWithdrawn()) throw new BusinessException(ErrorCode.USER_004);
        LocalDateTime now = LocalDateTime.now();

        // 복구 매칭용 해시만 남긴다. 원본 sub 는 지운다.
        if (authType == AuthType.GOOGLE && providerUserId != null) {
            this.restoreKey = JwtTokenProvider.sha256(provider + ":" + providerUserId);
        } else if (loginId != null) {
            this.restoreKey = JwtTokenProvider.sha256("LOCAL:" + loginId);
        }

        this.status = UserStatus.WITHDRAWN;
        this.withdrawReason = reason;
        this.withdrawnAt = now;
        this.purgeScheduledAt = now.plusDays(graceDays);

        this.email = null;
        this.providerUserId = null;
        this.loginId = null;
        this.passwordHash = null;
        this.nickname = "탈퇴한 사용자";
        this.profileImageUrl = null;
        this.bio = null;
        this.snsLinks = null;
    }

    public boolean isRestorable() {
        return isWithdrawn() && purgeScheduledAt != null
                && purgeScheduledAt.isAfter(LocalDateTime.now());
    }

    public void restore(AuthType authType, String loginId, String passwordHash,
                        String provider, String providerUserId, String nickname) {
        if (!isRestorable()) throw new BusinessException(ErrorCode.USER_005);
        this.status = UserStatus.ACTIVE;
        this.withdrawnAt = null;
        this.purgeScheduledAt = null;
        this.withdrawReason = null;
        this.restoreKey = null;
        this.authType = authType;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.nickname = nickname;
    }
}
