package com.ssafy.snaphere.domain.heatmap.service;

import com.ssafy.snaphere.domain.heatmap.entity.HeatmapPeriod;
import com.ssafy.snaphere.domain.heatmap.repository.HeatmapAggregationRepository;
import com.ssafy.snaphere.domain.tour.entity.SyncLog;
import com.ssafy.snaphere.domain.tour.entity.SyncType;
import com.ssafy.snaphere.domain.tour.repository.SyncLogRepository;
import com.ssafy.snaphere.global.util.GeoUtils;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 히트맵 집계 배치.
 *
 * REALTIME 은 1분 주기, 나머지 기간은 하루 1회로 나눈다.
 * REALTIME 만 자주 도는 이유: 홈 지도의 기본 레이어이고, 최근 1시간 데이터라 계산량이 작다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeatmapAggregationService {

    private final HeatmapAggregationRepository aggregationRepository;
    private final SyncLogRepository syncLogRepository;

    @Value("${app.heatmap.realtime-cache-seconds}") private int cacheSeconds;

    /** 실시간 레이어만 갱신. 격자 4단계 전부 다시 만든다. */
    @Transactional
    public int refreshRealtime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.plusSeconds(cacheSeconds);
        LocalDateTime from = HeatmapPeriod.REALTIME.from(now);

        int totalCells = 0;
        for (int level = 0; level <= 3; level++) {
            totalCells += aggregationRepository.rebuild(
                    level, HeatmapPeriod.REALTIME.name(), GeoUtils.gridSizeOf(level), from, next);
        }
        aggregationRepository.rebuildRegionStats();
        return totalCells;
    }

    /** DAY·WEEK·MONTH·ALL 레이어. 하루 1회로 충분하다. */
    @Transactional
    public int refreshLongPeriods() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.plusDays(1);

        int totalCells = 0;
        for (HeatmapPeriod period : new HeatmapPeriod[]{
                HeatmapPeriod.DAY, HeatmapPeriod.WEEK, HeatmapPeriod.MONTH, HeatmapPeriod.ALL}) {
            LocalDateTime from = period.from(now);
            for (int level = 0; level <= 3; level++) {
                totalCells += aggregationRepository.rebuild(
                        level, period.name(), GeoUtils.gridSizeOf(level), from, next);
            }
        }
        return totalCells;
    }

    /**
     * 업로드 직후 호출. 폴링 주기를 기다리지 않고 즉시 반영한다.
     * 시연 중 "사진을 올렸는데 지도에 안 뜬다" 를 막기 위한 장치다.
     *
     * ⚠️ 반드시 비동기다. 격자 4단계를 다시 만드는 작업이라 업로드 응답을 기다리게 하면 안 된다.
     *    게시물 트랜잭션이 커밋된 뒤에 도는 것이 정상이며, 실패해도 업로드는 성공으로 남는다.
     */
    @org.springframework.scheduling.annotation.Async("mediaExecutor")
    public void refreshAfterUploadAsync() {
        refreshAfterUpload();
    }

    @Transactional
    public void refreshAfterUpload() {
        try {
            refreshRealtime();
        } catch (Exception e) {
            // 업로드 자체가 실패하면 안 된다. 히트맵은 다음 주기에 맞춰진다.
            log.warn("업로드 후 히트맵 즉시 갱신 실패 — 다음 주기에 반영된다. 원인={}", e.getMessage());
        }
    }

    /** 스케줄러가 호출. 실행 기록을 남긴다. */
    public void refreshRealtimeLogged() {
        SyncLog syncLog = syncLogRepository.save(SyncLog.start(SyncType.HEATMAP, "REALTIME"));
        try {
            int cells = refreshRealtime();
            syncLog.succeed(cells, 0, "cells=" + cells);
        } catch (Exception e) {
            syncLog.fail(e.getMessage());
            log.error("히트맵 실시간 집계 실패", e);
        }
        syncLogRepository.save(syncLog);
    }
}
