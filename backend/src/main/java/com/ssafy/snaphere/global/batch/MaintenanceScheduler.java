package com.ssafy.snaphere.global.batch;

import com.ssafy.snaphere.domain.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 야간 배치 스케줄.
 *
 * 시간대를 겹치지 않게 배치했다. 같은 시각에 몰리면 DB 부하가 튀고 서로의 결과를 덮어쓴다.
 *   03:00 랭킹 집계   (posts 기준)
 *   03:30 히트맵 장기 (HeatmapScheduler)
 *   04:00 TourAPI 적재 (TourApiScheduler, 기본 OFF)
 *   05:00 TourAPI 축제
 *   05:30 카운터 보정·점수·파기  ← 다른 배치가 끝난 뒤 마지막에 정리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceScheduler {

    private final MaintenanceService maintenanceService;
    private final RankingService rankingService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void rebuildRankings() {
        log.info("[스케줄] 랭킹 집계 시작");
        rankingService.rebuildAll();
    }

    @Scheduled(cron = "0 30 5 * * *", zone = "Asia/Seoul")
    public void nightly() {
        log.info("[스케줄] 야간 유지보수 시작");
        maintenanceService.runNightly();
    }
}
