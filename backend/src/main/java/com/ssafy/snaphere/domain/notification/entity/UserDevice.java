package com.ssafy.snaphere.domain.notification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** FCM 토큰과 기기 정보. 계정 삭제 시 즉시 전부 지운다. */
@Getter
@Entity
@Table(name = "user_devices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_row_id")
    private Long id;

    @Column(name = "user_id", nullable = false)   private Long userId;
    @Column(name = "device_id", nullable = false, length = 100) private String deviceId;
    @Column(name = "fcm_token", length = 255)     private String fcmToken;
    @Column(nullable = false, length = 30)        private String platform;
    @Column(name = "app_version", length = 20)    private String appVersion;
    @Column(length = 10)                          private String locale;
    @Column(name = "push_enabled", nullable = false) private boolean pushEnabled;
    @Column(name = "last_active_at")               private LocalDateTime lastActiveAt;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private LocalDateTime updatedAt;
}
