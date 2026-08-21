package com.ssafy.snaphere.domain.auth.service;

import com.ssafy.snaphere.domain.auth.dto.AuthDtos;
import com.ssafy.snaphere.domain.user.entity.AuthType;
import com.ssafy.snaphere.domain.user.entity.RefreshToken;
import com.ssafy.snaphere.domain.user.entity.User;
import com.ssafy.snaphere.domain.user.entity.UserStatus;
import com.ssafy.snaphere.domain.user.repository.RefreshTokenRepository;
import com.ssafy.snaphere.domain.user.repository.UserRepository;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import com.ssafy.snaphere.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 8자 이상, 영문과 숫자를 각각 하나 이상 */
    private static final Pattern PASSWORD_RULE =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleTokenVerifier googleTokenVerifier;

    // ── 아이디 중복 확인 ────────────────────────────────────────
    @Transactional(readOnly = true)
    public AuthDtos.LoginIdAvailability checkLoginId(String loginId) {
        return new AuthDtos.LoginIdAvailability(!userRepository.existsByLoginId(loginId));
    }

    // ── 회원가입 ────────────────────────────────────────────────
    @Transactional
    public AuthDtos.TokenResponse signup(AuthDtos.SignupRequest req) {
        if (!req.password().equals(req.passwordConfirm())) {
            throw new BusinessException(ErrorCode.AUTH_002, "passwordConfirm");
        }
        if (!PASSWORD_RULE.matcher(req.password()).matches()) {
            throw new BusinessException(ErrorCode.AUTH_002, "password");
        }
        // 중복확인 API 호출 후 선점될 수 있으므로 가입 시점에 다시 검증한다.
        if (userRepository.existsByLoginId(req.loginId())) {
            throw new BusinessException(ErrorCode.AUTH_001, "loginId");
        }

        User user = userRepository.save(User.builder()
                .authType(AuthType.LOCAL)
                .loginId(req.loginId())
                .passwordHash(passwordEncoder.encode(req.password()))
                .email(req.email())
                .nickname(req.nickname())
                .build());
        user.agreeTerms();

        log.info("[SIGNUP] userId={} loginId={} hasEmail={}",
                user.getId(), req.loginId(), req.email() != null);
        return issueTokens(user, req.deviceId(), true, false);
    }

    // ── 아이디·비밀번호 로그인 ──────────────────────────────────
    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) {
        // ⚠️ 아이디가 없는 경우와 비밀번호가 틀린 경우를 같은 코드로 응답한다.
        //    구분해서 알려주면 "이 아이디가 존재한다"는 정보가 노출된다.
        User user = userRepository.findByLoginId(req.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_003));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_003);
        }

        // TODO(S3): 로그인 실패 횟수 누적 → 5회 시 5분 차단 (AUTH_007). Redis 또는 별도 테이블.
        return issueTokens(user, req.deviceId(), false, user.isWithdrawn());
    }

    // ── 구글 로그인 ────────────────────────────────────────────
    @Transactional
    public AuthDtos.TokenResponse googleLogin(AuthDtos.GoogleLoginRequest req) {
        GoogleTokenVerifier.GoogleProfile p = googleTokenVerifier.verify(req.idToken());

        var existing = userRepository.findByProviderAndProviderUserId("GOOGLE", p.sub());
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getStatus() == UserStatus.SUSPENDED) {
                throw new BusinessException(ErrorCode.USER_003);
            }
            return issueTokens(user, req.deviceId(), false, user.isWithdrawn());
        }

        // 탈퇴 유예 중인 같은 구글 계정이면 복구 안내를 위해 withdrawn=true 로 알려준다.
        var withdrawn = userRepository.findByRestoreKey(JwtTokenProvider.sha256("GOOGLE:" + p.sub()));
        if (withdrawn.isPresent() && withdrawn.get().isRestorable()) {
            User u = withdrawn.get();
            return new AuthDtos.TokenResponse(null, null, 0,
                    AuthDtos.UserSummary.from(u), false, true, u.getPurgeScheduledAt());
        }

        User created = userRepository.save(User.builder()
                .authType(AuthType.GOOGLE)
                .provider("GOOGLE")
                .providerUserId(p.sub())
                .email(p.email())
                .nickname(p.name() != null ? p.name() : "traveler")
                .profileImageUrl(p.picture())
                .build());

        log.info("[SIGNUP-GOOGLE] userId={}", created.getId());
        return issueTokens(created, req.deviceId(), true, false);
    }

    // ── 토큰 재발급 (로테이션) ──────────────────────────────────
    @Transactional
    public AuthDtos.TokenResponse refresh(String refreshToken) {
        Long userId = jwtTokenProvider.parseRefreshTokenUserId(refreshToken);
        if (userId == null) throw new BusinessException(ErrorCode.AUTH_005);

        String hash = JwtTokenProvider.sha256(refreshToken);
        RefreshToken saved = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_005));

        if (!saved.isUsable()) {
            // 이미 폐기된 토큰이 다시 왔다 = 탈취 가능성. 해당 사용자 전체 토큰을 무효화한다.
            log.warn("[TOKEN-REUSE] userId={} — 전체 토큰 무효화", userId);
            refreshTokenRepository.revokeAllByUserId(userId);
            throw new BusinessException(ErrorCode.AUTH_005);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_005));

        String next = jwtTokenProvider.createRefreshToken(user.getId());
        String nextHash = JwtTokenProvider.sha256(next);
        saved.rotate(nextHash);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId()).tokenHash(nextHash).deviceId(saved.getDeviceId())
                .expiresAt(LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshValiditySeconds()))
                .build());

        return new AuthDtos.TokenResponse(
                jwtTokenProvider.createAccessToken(user.getId(), user.getRole()),
                next, jwtTokenProvider.getAccessValiditySeconds(),
                AuthDtos.UserSummary.from(user), false, false, null);
    }

    // ── 로그아웃 ───────────────────────────────────────────────
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(JwtTokenProvider.sha256(refreshToken))
                .ifPresent(RefreshToken::revoke);
        // TODO(S11): 해당 deviceId 의 FCM 토큰도 제거
    }

    @Transactional
    public void logoutAll(Long userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("[LOGOUT-ALL] userId={} revoked={}", userId, revoked);
    }

    // ── 공통 ───────────────────────────────────────────────────
    private AuthDtos.TokenResponse issueTokens(User user, String deviceId,
                                               boolean isNew, boolean withdrawn) {
        if (withdrawn) {
            // 탈퇴 유예 중 — 토큰을 발급하지 않고 복구 안내만 돌려준다.
            return new AuthDtos.TokenResponse(null, null, 0,
                    AuthDtos.UserSummary.from(user), false, true, user.getPurgeScheduledAt());
        }
        String access = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refresh = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(JwtTokenProvider.sha256(refresh))
                .deviceId(deviceId)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshValiditySeconds()))
                .build());

        return new AuthDtos.TokenResponse(access, refresh,
                jwtTokenProvider.getAccessValiditySeconds(),
                AuthDtos.UserSummary.from(user), isNew, false, null);
    }
}
