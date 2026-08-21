package com.ssafy.snaphere.domain.notification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 인앱 알림 + FCM 발송 이력.
 *
 * ⚠️ messageParams 만 저장하고 완성된 문장은 저장하지 않는다.
 *    사용자가 앱 언어를 바꾸면 과거 알림도 그 언어로 보여야 한다.
 *    서버가 "민아님이 좋아합니다" 를 저장해버리면 영어 사용자에게 한국어가 그대로 남는다.
 */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "recipient_id", nullable = false) private Long recipientId;
    @Column(name = "actor_id")                       private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private NotificationTargetType targetType;

    @Column(name = "target_id") private Long targetId;

    @Column(name = "message_key", nullable = false, length = 50) private String messageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_params")
    private Map<String, String> messageParams;

    @Column(name = "thumbnail_url", length = 500) private String thumbnailUrl;

    @Column(name = "is_read", nullable = false) private boolean read;
    @Column(name = "read_at")       private LocalDateTime readAt;
    @Column(name = "push_sent_at")  private LocalDateTime pushSentAt;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;

    public static Notification of(Long recipientId, Long actorId, NotificationType type,
                                  NotificationTargetType targetType, Long targetId,
                                  Map<String, String> params, String thumbnailUrl) {
        Notification n = new Notification();
        n.recipientId = recipientId;
        n.actorId = actorId;
        n.type = type;
        n.targetType = targetType;
        n.targetId = targetId;
        n.messageKey = type.messageKey();
        n.messageParams = params;
        n.thumbnailUrl = thumbnailUrl;
        return n;
    }

    public void markRead() {
        if (!read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }

    public void markPushSent() { this.pushSentAt = LocalDateTime.now(); }
}
