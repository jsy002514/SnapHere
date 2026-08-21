package com.ssafy.snaphere.domain.notification.entity;

/**
 * 알림 종류. messageKey 는 프론트가 다국어 문구로 매핑하는 키다.
 * ⚠️ 서버는 완성된 문장을 만들지 않는다. 외국인 대상 서비스라 문구는 앱이 i18n 으로 처리한다.
 */
public enum NotificationType {
    POST_LIKE("notification.post_like"),
    COMMENT("notification.comment"),
    COMMENT_REPLY("notification.comment_reply"),
    FOLLOW("notification.follow"),
    FOLLOWEE_POST("notification.followee_post"),
    EVENT_NEARBY("notification.event_nearby"),
    GRADE_UP("notification.grade_up"),
    SYSTEM("notification.system");

    private final String messageKey;

    NotificationType(String messageKey) { this.messageKey = messageKey; }

    public String messageKey() { return messageKey; }
}
