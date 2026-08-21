package com.ssafy.snaphere.domain.user.service;

import com.ssafy.snaphere.domain.user.dto.UserDtos;
import com.ssafy.snaphere.domain.user.entity.AuthType;
import com.ssafy.snaphere.domain.user.entity.User;
import com.ssafy.snaphere.domain.user.repository.RefreshTokenRepository;
import com.ssafy.snaphere.domain.user.repository.UserRepository;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.account.purge-grace-days}")
    private int graceDays;

    @Value("${app.sns.allowed-hosts}")
    private List<String> allowedSnsHosts;

    @Transactional(readOnly = true)
    public UserDtos.MyProfile getMe(Long userId) {
        return UserDtos.MyProfile.from(getActive(userId));
    }

    @Transactional
    public UserDtos.MyProfile updateProfile(Long userId, UserDtos.ProfileUpdateRequest req) {
        User user = getActive(userId);
        Map<String, String> sns = req.snsLinks() == null ? null : validateSnsLinks(req.snsLinks());
        // TODO(S6): profileImageKey -> S3 공개 URL 변환
        user.updateProfile(req.nickname(), req.bio(), req.profileImageKey(), req.locale(), sns);
        return UserDtos.MyProfile.from(user);
    }

    /**
     * SNS 링크는 허용 도메인만 받는다.
     * 검증 없이 받으면 프로필이 그대로 피싱 링크 유포 통로가 된다.
     */
    private Map<String, String> validateSnsLinks(Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<>();
        input.forEach((k, v) -> {
            if (v == null || v.isBlank()) return;
            try {
                URI uri = URI.create(v);
                if (!"https".equalsIgnoreCase(uri.getScheme())) {
                    throw new BusinessException(ErrorCode.USER_006, k);
                }
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
                if (!allowedSnsHosts.contains(host)) {
                    throw new BusinessException(ErrorCode.USER_006, k);
                }
                result.put(k, v);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.USER_006, k);
            }
        });
        return result;
    }

    @Transactional
    public void changePassword(Long userId, UserDtos.PasswordChangeRequest req) {
        User user = getActive(userId);
        if (user.getAuthType() != AuthType.LOCAL || user.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.COMMON_400, "authType");
        }
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_003, "currentPassword");
        }
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        // 비밀번호가 바뀌면 다른 기기 세션은 모두 끊는다.
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    @Transactional
    public void agreeTerms(Long userId) {
        getActive(userId).agreeTerms();
    }

    @Transactional
    public void updateNotificationSettings(Long userId, UserDtos.NotificationSettingRequest req) {
        getActive(userId).updateNotificationSettings(
                req.like(), req.comment(), req.follow(), req.followeePost());
    }

    @Transactional(readOnly = true)
    public UserDtos.DeletionPreview deletionPreview(Long userId) {
        User user = getActive(userId);
        // TODO(S8~S12): post/media/comment/visit/ranking 리포지토리 연결 후 실제 카운트로 교체
        return new UserDtos.DeletionPreview(
                user.getPostCount(), 0, 0, user.getFollowerCount(), user.getVisitCount(), 0, graceDays);
    }

    @Transactional
    public UserDtos.DeleteResult deleteAccount(Long userId, UserDtos.AccountDeleteRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_401));
        if (user.isWithdrawn()) throw new BusinessException(ErrorCode.USER_004);

        // LOCAL 계정은 본인 확인
        if (user.getAuthType() == AuthType.LOCAL) {
            if (req.password() == null
                    || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.AUTH_003, "password");
            }
        }

        // TODO(S8~S12) 순서대로 붙일 것
        //  1) contentAction == DELETE_ALL 이면 posts / comments 논리 삭제
        //  2) follows / likes / bookmarks 즉시 삭제 + 상대 카운터 감소
        //  3) user_devices(FCM) 전부 삭제
        //  4) account_deletion_logs 에 감사 로그 INSERT
        refreshTokenRepository.revokeAllByUserId(userId);

        String reason = req.reason() == null ? null : req.reason().name();
        user.withdraw(reason, graceDays);

        log.info("[ACCOUNT-DELETE] userId={} action={} reason={} purgeAt={}",
                userId, req.contentAction(), reason, user.getPurgeScheduledAt());

        return new UserDtos.DeleteResult(user.getWithdrawnAt(), user.getPurgeScheduledAt(),
                req.contentAction(), 0, 0,
                user.isRestorable() ? user.getPurgeScheduledAt() : null);
    }

    private User getActive(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_401));
        if (user.isWithdrawn()) throw new BusinessException(ErrorCode.USER_004);
        if (!user.isActive()) throw new BusinessException(ErrorCode.USER_003);
        return user;
    }
}
