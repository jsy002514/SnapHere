package com.ssafy.snaphere.domain.notification.controller;

import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * FCM 토큰 등록. 앱이 시작할 때마다 호출한다(토큰은 갱신될 수 있다).
 *
 * ⚠️ 계정 삭제 시 이 테이블의 행을 즉시 전부 지운다. 토큰이 남아 있으면
 *    탈퇴한 사용자의 기기로 푸시가 계속 갈 수 있다.
 */
@Tag(name = "기기 · 푸시 토큰")
@RestController
@RequestMapping("/api/v1/users/me/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final JdbcTemplate jdbcTemplate;

    public record RegisterRequest(
            @NotBlank String deviceId,
            String fcmToken,
            @NotBlank String platform,
            String appVersion,
            String locale,
            Boolean pushEnabled) {}

    @Operation(summary = "FCM 토큰 등록 · 갱신")
    @PostMapping
    public ApiResponse<Void> register(@AuthUser LoginUser me, @RequestBody RegisterRequest req) {
        // 같은 (user, device) 는 갱신한다. 앱 재설치로 토큰이 바뀌어도 행이 늘어나지 않는다.
        jdbcTemplate.update("""
                INSERT INTO user_devices
                    (user_id, device_id, fcm_token, platform, app_version, locale, push_enabled, last_active_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(6)) AS incoming
                ON DUPLICATE KEY UPDATE
                    fcm_token = incoming.fcm_token,
                    platform = incoming.platform,
                    app_version = incoming.app_version,
                    locale = incoming.locale,
                    push_enabled = incoming.push_enabled,
                    last_active_at = NOW(6)
                """,
                me.userId(), req.deviceId(), req.fcmToken(),
                normalizePlatform(req.platform()), req.appVersion(), req.locale(),
                req.pushEnabled() == null || req.pushEnabled() ? 1 : 0);
        return ApiResponse.ok();
    }

    @Operation(summary = "기기 삭제 (로그아웃 시)")
    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> remove(@AuthUser LoginUser me, @PathVariable String deviceId) {
        jdbcTemplate.update("DELETE FROM user_devices WHERE user_id = ? AND device_id = ?",
                me.userId(), deviceId);
        return ApiResponse.ok();
    }

    private static String normalizePlatform(String raw) {
        if (raw == null) return "WEB";
        String p = raw.trim().toUpperCase();
        return switch (p) {
            case "IOS", "ANDROID", "WEB" -> p;
            default -> "WEB";
        };
    }
}
