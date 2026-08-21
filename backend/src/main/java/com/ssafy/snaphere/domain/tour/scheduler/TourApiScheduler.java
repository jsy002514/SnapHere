package com.ssafy.snaphere.domain.tour.scheduler;

import com.ssafy.snaphere.domain.tour.client.TourApiClient;
import com.ssafy.snaphere.domain.tour.service.TourApiSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TourAPI 배치 스케줄.
 *
 * ⚠️ 기본값은 꺼져 있다(app.tour-api.scheduler-enabled: false).
 *    로컬에서 서버를 띄울 때마다 외부 API 를 수천 번 호출하면 일 호출 한도가 바로 소진된다.
 *    운영 서버에서만 true 로 켠다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.tour-api", name = "scheduler-enabled", havingValue = "true")
public class TourApiScheduler {

    private final TourApiSyncService syncService;
    private final TourApiClient client;

    /** 자정 — 일 호출 예산 리셋 */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void resetBudget() {
        client.resetDailyBudget();
    }

    /** 매일 04:00 — 관광지 증분 동기화. 트래픽이 가장 적은 시간대로 잡았다. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncPlaces() {
        log.info("[스케줄] TourAPI 관광지 동기화 시작");
        syncService.syncAllPlaces();
    }

    /** 매일 05:00 — 축제·행사. 관광지 적재가 끝난 뒤에 돌린다. */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void syncFestivals() {
        log.info("[스케줄] TourAPI 축제 동기화 시작");
        syncService.syncFestivals();
    }
}
