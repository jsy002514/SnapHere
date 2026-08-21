package com.ssafy.snaphere.domain.notification.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 알림 생성은 JPA 대신 직접 SQL 로 한다.
 *
 * 이유: uk_notifications_dedup (recipient, actor, type, target) 유니크 키에 걸리는 중복을
 * INSERT IGNORE 로 흡수해야 한다. "좋아요 → 취소 → 좋아요" 를 반복해도 알림이 쌓이지 않아야 하고,
 * JPA 로 하면 예외 처리와 트랜잭션 롤백이 얽힌다.
 */
@Repository
@RequiredArgsConstructor
public class NotificationWriteRepository {

    private final JdbcTemplate jdbcTemplate;

    /** @return true 면 새 알림이 생성됨(중복이면 false) */
    public boolean insertIgnore(Long recipientId, Long actorId, String type,
                                String targetType, Long targetId,
                                String messageKey, String messageParamsJson, String thumbnailUrl) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO notifications
                    (recipient_id, actor_id, type, target_type, target_id,
                     message_key, message_params, thumbnail_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, recipientId, actorId, type, targetType, targetId,
                messageKey, messageParamsJson, thumbnailUrl) > 0;
    }

    /** 발송 성공 표시 */
    public void markPushSent(Long recipientId, String type, Long targetId) {
        jdbcTemplate.update("""
                UPDATE notifications SET push_sent_at = NOW(6)
                WHERE recipient_id = ? AND type = ? AND target_id = ? AND push_sent_at IS NULL
                """, recipientId, type, targetId);
    }

    /** 푸시를 받을 토큰 목록. 사용자별 알림 스위치와 기기별 OS 권한을 모두 확인한다. */
    public List<String> findPushTokens(Long userId, String settingColumn) {
        // settingColumn 은 코드에서만 넘기는 화이트리스트 값이다(사용자 입력을 넣지 말 것).
        return jdbcTemplate.queryForList("""
                SELECT d.fcm_token
                FROM user_devices d
                JOIN users u ON u.user_id = d.user_id
                WHERE d.user_id = ?
                  AND d.fcm_token IS NOT NULL
                  AND d.push_enabled = 1
                  AND u.status = 'ACTIVE'
                  AND u.%s = 1
                """.formatted(settingColumn), String.class, userId);
    }
}
