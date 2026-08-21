package com.ssafy.snaphere.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.snaphere.domain.notification.entity.*;
import com.ssafy.snaphere.domain.notification.push.PushSender;
import com.ssafy.snaphere.domain.notification.repository.*;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.common.PageResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 생성 + 푸시 발송.
 *
 * ⚠️ 발송은 반드시 비동기다. 좋아요를 눌렀을 때 FCM 응답을 기다리면
 *    외부 서비스가 느려질 때 좋아요 API 전체가 같이 느려진다.
 * ⚠️ 알림 실패가 원래 작업(좋아요·댓글·팔로우)을 되돌리면 안 된다. 예외를 밖으로 던지지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 알림 종류 → users 테이블의 스위치 컬럼. 코드에서만 넘기는 화이트리스트. */
    private static final Map<NotificationType, String> SETTING_COLUMN = Map.of(
            NotificationType.POST_LIKE, "push_like_enabled",
            NotificationType.COMMENT, "push_comment_enabled",
            NotificationType.COMMENT_REPLY, "push_comment_enabled",
            NotificationType.FOLLOW, "push_follow_enabled",
            NotificationType.FOLLOWEE_POST, "push_post_enabled");

    private final NotificationRepository notificationRepository;
    private final NotificationWriteRepository writeRepository;
    private final PushSender pushSender;
    private final ObjectMapper objectMapper;

    /**
     * 알림 생성 + 푸시. 호출한 쪽 트랜잭션과 분리해 비동기로 돈다.
     *
     * @param recipientId 받는 사람. actor 와 같으면(자기 글에 자기가 좋아요) 아무것도 하지 않는다.
     */
    @Async("notificationExecutor")
    public void notifyAsync(Long recipientId, Long actorId, NotificationType type,
                            NotificationTargetType targetType, Long targetId,
                            Map<String, String> params, String thumbnailUrl) {
        try {
            notify(recipientId, actorId, type, targetType, targetId, params, thumbnailUrl);
        } catch (Exception e) {
            // 알림 실패가 좋아요·댓글·팔로우를 되돌리면 안 된다.
            log.warn("알림 발송 실패 recipient={} type={} target={} 원인={}",
                    recipientId, type, targetId, e.getMessage());
        }
    }

    @Transactional
    public void notify(Long recipientId, Long actorId, NotificationType type,
                       NotificationTargetType targetType, Long targetId,
                       Map<String, String> params, String thumbnailUrl) {

        if (recipientId == null) return;
        // 자기 자신에게는 알리지 않는다. 내 글에 내가 좋아요를 눌러도 알림이 오면 이상하다.
        if (recipientId.equals(actorId)) return;

        String json = toJson(params);
        boolean created = writeRepository.insertIgnore(
                recipientId, actorId, type.name(),
                targetType == null ? null : targetType.name(), targetId,
                type.messageKey(), json, thumbnailUrl);

        // 이미 같은 알림이 있으면 푸시도 보내지 않는다(좋아요 취소·재클릭 스팸 방지).
        if (!created) return;

        String settingColumn = SETTING_COLUMN.get(type);
        if (settingColumn == null) return;   // SYSTEM 등은 인앱만

        List<String> tokens = writeRepository.findPushTokens(recipientId, settingColumn);
        if (tokens.isEmpty()) return;

        int sent = pushSender.send(tokens, type.messageKey(), params, thumbnailUrl);
        if (sent > 0) writeRepository.markPushSent(recipientId, type.name(), targetId);
    }

    // ── 조회 ──

    @Transactional(readOnly = true)
    public PageResponse<NotificationItem> list(Long userId, PageRequestParam pageParam) {
        var page = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(
                userId, pageParam.toPageable());
        return PageResponse.from(page, NotificationItem::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public int markRead(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return notificationRepository.markAllRead(userId);
        return notificationRepository.markRead(userId, ids);
    }

    private String toJson(Map<String, String> params) {
        if (params == null || params.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return null;
        }
    }

    public record NotificationItem(
            Long notificationId, String type, String targetType, Long targetId,
            String messageKey, Map<String, String> messageParams,
            String thumbnailUrl, boolean isRead, java.time.LocalDateTime createdAt) {

        public static NotificationItem from(Notification n) {
            return new NotificationItem(n.getId(), n.getType().name(),
                    n.getTargetType() == null ? null : n.getTargetType().name(),
                    n.getTargetId(), n.getMessageKey(), n.getMessageParams(),
                    n.getThumbnailUrl(), n.isRead(), n.getCreatedAt());
        }
    }
}
