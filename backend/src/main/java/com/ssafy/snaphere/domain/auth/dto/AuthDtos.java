package com.ssafy.snaphere.domain.auth.dto;

import com.ssafy.snaphere.domain.user.entity.AuthType;
import com.ssafy.snaphere.domain.user.entity.Grade;
import com.ssafy.snaphere.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/** 인증 도메인 DTO. docs/03_API명세서.md 2장과 1:1. */
public final class AuthDtos {

    private AuthDtos() {}

    // ───────── Request ─────────

    @Schema(name = "SignupRequest")
    public record SignupRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9_]{4,20}$",
                    message = "아이디는 4~20자의 영문 소문자·숫자·밑줄만 사용할 수 있습니다.")
            String loginId,

            @NotBlank @Size(min = 8, max = 64)
            String password,

            @NotBlank String passwordConfirm,

            @NotBlank @Size(min = 2, max = 20) String nickname,

            @Email @Schema(description = "선택. 없으면 비밀번호 찾기가 불가능합니다.")
            String email,

            @AssertTrue(message = "이용 약관에 동의해야 합니다.") boolean termsAgreed,

            String deviceId,
            String platform
    ) {}

    @Schema(name = "LoginRequest")
    public record LoginRequest(
            @NotBlank String loginId,
            @NotBlank String password,
            String deviceId,
            String platform
    ) {}

    @Schema(name = "GoogleLoginRequest")
    public record GoogleLoginRequest(
            @NotBlank String idToken,
            String deviceId,
            String platform
    ) {}

    @Schema(name = "RefreshRequest")
    public record RefreshRequest(@NotBlank String refreshToken) {}

    @Schema(name = "LogoutRequest")
    public record LogoutRequest(@NotBlank String refreshToken, String deviceId) {}

    // ───────── Response ─────────

    @Schema(name = "TokenResponse")
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresIn,
            UserSummary user,
            boolean isNewUser,
            boolean withdrawn,
            LocalDateTime restorableUntil
    ) {}

    @Schema(name = "UserSummary")
    public record UserSummary(
            Long userId,
            AuthType authType,
            String nickname,
            String profileImageUrl,
            Grade grade,
            int popularityScore,
            boolean termsAgreed,
            String locale
    ) {
        public static UserSummary from(User u) {
            return new UserSummary(u.getId(), u.getAuthType(), u.getNickname(),
                    u.getProfileImageUrl(), u.getGrade(), u.getPopularityScore(),
                    u.hasAgreedTerms(), u.getLocale());
        }
    }

    @Schema(name = "LoginIdAvailability")
    public record LoginIdAvailability(boolean available) {}
}
