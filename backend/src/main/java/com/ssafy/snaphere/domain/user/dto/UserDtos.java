package com.ssafy.snaphere.domain.user.dto;

import com.ssafy.snaphere.domain.user.entity.AuthType;
import com.ssafy.snaphere.domain.user.entity.Grade;
import com.ssafy.snaphere.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

public final class UserDtos {

    private UserDtos() {}

    public enum ContentAction {
        /** 게시물 유지, 작성자만 "탈퇴한 사용자"로 표시 (랭킹 점수 유지) */
        KEEP_ANONYMIZED,
        /** 게시물·댓글 전부 삭제 */
        DELETE_ALL
    }

    public enum WithdrawReason { NOT_USING, PRIVACY, HARD_TO_USE, FOUND_ALTERNATIVE, OTHER, ADMIN_FORCED }

    // ───────── Request ─────────

    @Schema(name = "ProfileUpdateRequest")
    public record ProfileUpdateRequest(
            @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.") String nickname,
            @Size(max = 150) String bio,
            String profileImageKey,
            String locale,
            @Schema(description = "허용 도메인만 저장됩니다 (instagram·youtube·tiktok 등)")
            Map<String, String> snsLinks
    ) {}

    @Schema(name = "PasswordChangeRequest")
    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 64) String newPassword
    ) {}

    @Schema(name = "NotificationSettingRequest")
    public record NotificationSettingRequest(Boolean like, Boolean comment,
                                             Boolean follow, Boolean followeePost) {}

    @Schema(name = "AccountDeleteRequest")
    public record AccountDeleteRequest(
            @NotNull ContentAction contentAction,
            WithdrawReason reason,
            @Schema(description = "LOCAL 계정은 본인 확인용으로 필요") String password
    ) {}

    // ───────── Response ─────────

    @Schema(name = "MyProfileResponse")
    public record MyProfile(
            Long userId, AuthType authType, String loginId,
            String nickname, String profileImageUrl, String bio, String email, String locale,
            Grade grade, int popularityScore, Integer nextGradeScore,
            Stats stats, Map<String, String> snsLinks,
            boolean termsAgreed, LocalDateTime uploadBlockedUntil,
            NotificationSettings notificationSettings
    ) {
        public static MyProfile from(User u) {
            return new MyProfile(u.getId(), u.getAuthType(), u.getLoginId(),
                    u.getNickname(), u.getProfileImageUrl(), u.getBio(), u.getEmail(), u.getLocale(),
                    u.getGrade(), u.getPopularityScore(), u.getGrade().nextScore(),
                    new Stats(u.getPostCount(), u.getFollowerCount(), u.getFollowingCount(), u.getVisitCount()),
                    u.getSnsLinks(), u.hasAgreedTerms(), u.getUploadBlockedUntil(),
                    new NotificationSettings(u.isPushLikeEnabled(), u.isPushCommentEnabled(),
                            u.isPushFollowEnabled(), u.isPushPostEnabled()));
        }
    }

    public record Stats(int postCount, int followerCount, int followingCount, int visitCount) {}

    public record NotificationSettings(boolean like, boolean comment,
                                       boolean follow, boolean followeePost) {}

    @Schema(name = "PublicProfileResponse")
    public record PublicProfile(
            Long userId, String nickname, String profileImageUrl, String bio,
            Grade grade, int popularityScore, Stats stats, Map<String, String> snsLinks,
            boolean isFollowing, boolean isFollowedBy, boolean isMe
    ) {}

    @Schema(name = "DeletionPreviewResponse")
    public record DeletionPreview(
            int postCount, int mediaCount, int commentCount,
            int followerCount, int visitCount, int rankedPlaceCount, int graceDays
    ) {}

    @Schema(name = "AccountDeleteResponse")
    public record DeleteResult(
            LocalDateTime deletedAt, LocalDateTime purgeScheduledAt,
            ContentAction contentAction, int deletedPosts, int deletedComments,
            LocalDateTime restorableUntil
    ) {}
}
