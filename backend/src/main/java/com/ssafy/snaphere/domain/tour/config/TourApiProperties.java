package com.ssafy.snaphere.domain.tour.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.tour-api 블록.
 *
 * ⚠️ serviceKey 는 절대 이 클래스의 기본값이나 코드에 넣지 않는다.
 *    application-local.yml (gitignore 대상) 또는 환경변수 TOUR_API_KEY 로만 주입한다.
 */
@ConfigurationProperties(prefix = "app.tour-api")
public record TourApiProperties(
        String baseUrl,
        String serviceKey,
        String mobileOs,
        String mobileApp,
        int numOfRows,
        int connectTimeoutMs,
        int readTimeoutMs,
        List<Integer> contentTypeIds,
        int dailyCallBudget,
        boolean schedulerEnabled
) {
    public boolean hasKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
