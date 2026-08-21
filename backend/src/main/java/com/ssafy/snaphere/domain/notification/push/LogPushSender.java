package com.ssafy.snaphere.domain.notification.push;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 개발용. 실제로 보내지 않고 로그만 남긴다.
 * 인앱 알림은 정상 동작하므로 프론트는 알림 목록·안읽은수 기능을 그대로 개발할 수 있다.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "fcmPushSender")
public class LogPushSender implements PushSender {

    @Override
    public int send(List<String> fcmTokens, String messageKey,
                    Map<String, String> params, String thumbnailUrl) {
        if (fcmTokens.isEmpty()) return 0;
        log.info("[PUSH-DRYRUN] tokens={} key={} params={} (FCM 미설정 — 실제 발송 안 함)",
                fcmTokens.size(), messageKey, params);
        return fcmTokens.size();
    }
}
