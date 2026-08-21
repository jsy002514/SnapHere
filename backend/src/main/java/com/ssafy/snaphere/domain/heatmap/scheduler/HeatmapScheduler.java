package com.ssafy.snaphere.domain.heatmap.scheduler;

import com.ssafy.snaphere.domain.heatmap.service.HeatmapAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 히트맵 집계 스케줄.
 *
 * TourApiScheduler 와 달리 기본 ON 이다. 외부 API 를 호출하지 않고 우리 DB 만 읽으므로
 * 로컬에서 켜져 있어도 부작용이 없고, 오히려 꺼져 있으면 지도가 계속 비어 보인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeatmapScheduler {

    private final HeatmapAggregationService aggregationService;

    /** 1분 주기 — 실시간 레이어. 앱의 60초 폴링과 맞춘다. */
    @Scheduled(fixedDelayString = "${app.heatmap.realtime-refresh-ms:60000}",
               initialDelayString = "10000")
    public void refreshRealtime() {
        aggregationService.refreshRealtimeLogged();
    }

    /** 매일 03:30 — 장기 기간 레이어. TourAPI 적재(04:00) 보다 앞에 둬 겹치지 않게 한다. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void refreshLongPeriods() {
        log.info("[스케줄] 히트맵 장기 기간 집계 시작");
        aggregationService.refreshLongPeriods();
    }
}
