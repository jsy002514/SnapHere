package com.ssafy.snaphere.domain.tour.service;

import com.ssafy.snaphere.domain.tour.client.TourApiCallException;
import com.ssafy.snaphere.domain.tour.client.TourApiClient;
import com.ssafy.snaphere.domain.tour.config.TourApiProperties;
import com.ssafy.snaphere.domain.tour.dto.TourApiDtos.AreaCodeItem;
import com.ssafy.snaphere.domain.tour.entity.SyncLog;
import com.ssafy.snaphere.domain.tour.entity.SyncType;
import com.ssafy.snaphere.domain.tour.repository.SyncLogRepository;
import com.ssafy.snaphere.domain.tour.repository.TourUpsertRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * TourAPI 적재 오케스트레이터.
 *
 * 설계 원칙
 *  1) 이 클래스는 트랜잭션을 걸지 않는다. 조합 하나가 실패해도 나머지를 계속 돌려야 하기 때문이다.
 *  2) 조합 단위 결과를 sync_logs 에 남긴다. 실패한 조합만 골라 재실행할 수 있어야 한다.
 *  3) 사용자 요청 경로에서 호출하지 않는다. 스케줄러 또는 관리자 수동 트리거만.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiSyncService {

    /** 17개 시도. TourAPI areaCode 는 8 다음이 31 이다. 1~17 로 재번호를 매기지 말 것. */
    private static final List<Integer> AREA_CODES =
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 31, 32, 33, 34, 35, 36, 37, 38, 39);

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TourApiClient client;
    private final TourApiProperties props;
    private final TourPlaceIngestService ingestService;
    private final TourUpsertRepository upsertRepository;
    private final SyncLogRepository syncLogRepository;

    // ── 1. 지역 · 시군구 마스터 (최초 1회, 이후 가끔) ──────────────

    public SyncLog syncAreaCodes() {
        SyncLog syncLog = syncLogRepository.save(SyncLog.start(SyncType.TOUR_API_AREA, "areaCode2"));
        try {
            // ⚠️ 지역 이름은 우리가 관리한다. TourAPI 값으로 덮어쓰지 않고 존재만 확인한다.
            //    (덮어썼다가 이름이 '??' 로 유실된 사고가 있었다 — TourUpsertRepository 주석 참고)
            List<AreaCodeItem> regions = client.fetchAreaCodes();
            int regionUpdated = upsertRepository.verifyRegions(regions);

            int sigunguTotal = 0;
            for (Integer areaCode : AREA_CODES) {
                try {
                    sigunguTotal += upsertRepository.upsertSigungu(areaCode, client.fetchSigunguCodes(areaCode));
                } catch (TourApiCallException e) {
                    log.warn("시군구 적재 실패 areaCode={} 원인={}", areaCode, e.getMessage());
                }
            }
            syncLog.succeed(sigunguTotal, regionUpdated);
            log.info("지역 마스터 적재 완료. 시도 확인={}/17 시군구={}", regionUpdated, sigunguTotal);
        } catch (Exception e) {
            syncLog.fail(e.getMessage());
            log.error("지역 마스터 적재 실패", e);
        }
        return syncLogRepository.save(syncLog);
    }

    // ── 2. 관광지 전체 (매일 04:00) ──────────────────────────────

    /**
     * 17개 시도 × contentTypeId 조합을 순회한다.
     * 조합 하나가 실패해도 다음 조합을 계속 진행하고, 결과는 조합별로 sync_logs 에 남는다.
     */
    public void syncAllPlaces() {
        List<Integer> typeIds = props.contentTypeIds();
        log.info("TourAPI 관광지 적재 시작. 조합 {}개 (지역 {} × 유형 {}), 남은 호출 예산={}",
                AREA_CODES.size() * typeIds.size(), AREA_CODES.size(), typeIds.size(),
                client.callBudgetRemaining());

        int okCombinations = 0, failedCombinations = 0;

        for (Integer areaCode : AREA_CODES) {
            for (Integer contentTypeId : typeIds) {

                if (client.callBudgetRemaining() <= 0) {
                    log.warn("일 호출 예산 소진. areaCode={} contentTypeId={} 이후 조합을 중단한다.",
                            areaCode, contentTypeId);
                    return;
                }

                String target = "areaCode=" + areaCode + ",contentTypeId=" + contentTypeId;
                SyncLog syncLog = syncLogRepository.save(SyncLog.start(SyncType.TOUR_API_DETAIL, target));
                try {
                    int[] r = ingestService.ingestCombination(areaCode, contentTypeId);
                    syncLog.succeed(r[0], r[1], "fetched=" + r[3] + " skipped=" + r[2]);
                    okCombinations++;
                    log.info("적재 완료 {} 신규={} 기존={} 스킵={} API총건수={}", target, r[0], r[1], r[2], r[3]);
                } catch (Exception e) {
                    syncLog.fail(e.getMessage());
                    failedCombinations++;
                    log.warn("적재 실패 {} 원인={}", target, e.getMessage());
                }
                syncLogRepository.save(syncLog);
            }
        }
        log.info("TourAPI 관광지 적재 종료. 성공 조합={} 실패 조합={} 사용 호출={}",
                okCombinations, failedCombinations, client.callsUsedToday());
    }

    /** 단일 조합만 재실행 (실패한 조합 복구용). */
    public SyncLog syncOnePlaceCombination(int areaCode, int contentTypeId) {
        String target = "areaCode=" + areaCode + ",contentTypeId=" + contentTypeId;
        SyncLog syncLog = syncLogRepository.save(SyncLog.start(SyncType.TOUR_API_DETAIL, target));
        try {
            int[] r = ingestService.ingestCombination(areaCode, contentTypeId);
            syncLog.succeed(r[0], r[1], "fetched=" + r[3] + " skipped=" + r[2]);
        } catch (Exception e) {
            syncLog.fail(e.getMessage());
        }
        return syncLogRepository.save(syncLog);
    }

    // ── 3. 축제 · 행사 (이벤트 탭) ────────────────────────────────

    /**
     * 오늘 이후 열리는 축제·행사를 적재한다. 이벤트 탭이 이 데이터에 의존한다.
     * 행사 기간(eventstartdate/eventenddate)은 searchFestival2 에서만 제대로 온다.
     */
    public SyncLog syncFestivals() {
        String from = LocalDate.now().format(YMD);
        SyncLog syncLog = syncLogRepository.save(
                SyncLog.start(SyncType.TOUR_API_FESTIVAL, "eventStartDate=" + from));
        try {
            int[] r = ingestService.ingestFestivals(from, null);
            syncLog.succeed(r[0], r[1], "fetched=" + r[3] + " skipped=" + r[2]);
            log.info("축제 적재 완료 신규={} 기존={} 스킵={} API총건수={}", r[0], r[1], r[2], r[3]);
        } catch (Exception e) {
            syncLog.fail(e.getMessage());
            log.warn("축제 적재 실패 원인={}", e.getMessage());
        }
        return syncLogRepository.save(syncLog);
    }
}
