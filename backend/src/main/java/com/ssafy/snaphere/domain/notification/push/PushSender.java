package com.ssafy.snaphere.domain.notification.push;

import java.util.List;
import java.util.Map;

/**
 * 푸시 발송 추상화.
 *
 * MediaStorage 와 같은 이유로 인터페이스를 둔다. Firebase 서비스 계정 키가 없어도
 * 알림 생성·조회·읽음처리 흐름 전체를 개발하고 시연할 수 있어야 한다.
 *
 * FCM 으로 교체하는 방법
 *   1) build.gradle 의 firebase-admin 의존성 주석을 푼다
 *   2) 이 인터페이스를 구현한 FcmPushSender 를 만든다 (firebase-service-account.json 은 gitignore 대상)
 *   3) LogPushSender 는 @ConditionalOnMissingBean 이라 자동으로 비활성화된다
 *
 * ⚠️ 페이로드에 완성된 문장을 넣지 않는다. messageKey + params 만 보내고 문구는 앱이 만든다.
 */
public interface PushSender {

    /** @return 발송에 성공한 토큰 수 */
    int send(List<String> fcmTokens, String messageKey, Map<String, String> params, String thumbnailUrl);
}
