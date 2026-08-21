package com.ssafy.snaphere.domain.ranking.service;

import com.ssafy.snaphere.domain.ranking.repository.RankingRepository;
import com.ssafy.snaphere.domain.tour.entity.SyncLog;
import com.ssafy.snaphere.domain.tour.entity.SyncType;
import com.ssafy.snaphere.domain.tour.repository.SyncLogRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랭킹 집계·조회.
 *
 * 조회 시점에 계산하지 않는다. 커뮤니티 홈에서 매번 posts 를 GROUP BY 하면
 * 게시물이 늘어날수록 진입이 느려지고, 같은 화면을 여러 사람이 보면 그대로 곱해진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    /** 17개 시도 + 전국(0) */
    private static final List<Integer> AREA_CODES =
            List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 31, 32, 33, 34, 35, 36, 37, 38, 39);

    /** period → 집계 기간(일). ALL_TIME 은 null */
    private static final Map<String, Integer> PERIOD_DAYS = Map.of(
            "DAILY", 1, "WEEKLY", 7, "MONTHLY", 30);

    private static final String THEME_ALL = "ALL";

    private final RankingRepository rankingRepository;
    private final SyncLogRepository syncLogRepository;

    @Value("${app.ranking.on-site-weight}")             private double onSiteWeight;
    @Value("${app.ranking.location-confirmed-weight}")  private double confirmedWeight;
    @Value("${app.ranking.like-weight}")                private double likeWeight;
    @Value("${app.ranking.comment-weight}")             private double commentWeight;
    @Value("${app.ranking.top-n}")                      private int topN;
    @Value("${app.ranking.history-keep-days}")          private int historyKeepDays;

    // ── 집계 ──────────────────────────────────────────────────

    /**
     * ⚠️ @Transactional 이 필수다. RankingRepository.rebuild 가 임시 테이블을 쓰므로
     *    같은 커넥션에서 4단계가 이어져야 한다.
     */
    @Transactional
    public int rebuildOne(int areaCode, String period) {
        Integer days = PERIOD_DAYS.get(period);
        int ranked = rankingRepository.rebuild(areaCode, period, THEME_ALL, days,
                onSiteWeight, confirmedWeight, likeWeight, commentWeight, topN);
        rankingRepository.snapshot(areaCode, period, THEME_ALL);
        return ranked;
    }

    /** 전체 재계산. 18개 지역 × 4개 기간 = 72 슬롯. */
    public void rebuildAll() {
        SyncLog syncLog = syncLogRepository.save(SyncLog.start(SyncType.RANKING, "ALL"));
        int total = 0, failed = 0;
        for (Integer areaCode : AREA_CODES) {
            for (String period : List.of("DAILY", "WEEKLY", "MONTHLY", "ALL_TIME")) {
                try {
                    total += rebuildOne(areaCode, period);
                } catch (Exception e) {
                    failed++;
                    log.warn("랭킹 집계 실패 areaCode={} period={} 원인={}", areaCode, period, e.getMessage());
                }
            }
        }
        try {
            rankingRepository.rebuildRegionTagStats();
            rankingRepository.pruneOldHistory(historyKeepDays);
        } catch (Exception e) {
            log.warn("태그 집계·이력 정리 실패: {}", e.getMessage());
        }
        if (failed > 0) syncLog.fail("실패 슬롯 " + failed + "개, 성공 항목 " + total + "개");
        else syncLog.succeed(total, 0, "슬롯 " + (AREA_CODES.size() * 4) + "개 집계 완료");
        syncLogRepository.save(syncLog);
        log.info("랭킹 집계 종료. 항목={} 실패슬롯={}", total, failed);
    }

    // ── 조회 ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RankingResponse places(Integer areaCode, String period, int limit) {
        int area = areaCode == null ? 0 : areaCode;
        String p = normalizePeriod(period);
        List<Map<String, Object>> items = rankingRepository.findRanking(area, p, THEME_ALL, limit);
        return new RankingResponse(area, p, THEME_ALL, items);
    }

    /**
     * 추천 장소. 랭킹이 비어 있으면 fallback 으로 채운다.
     * 데이터가 적은 초기·시연 상황에서 커뮤니티 홈이 텅 비는 것을 막는 장치다.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recommendations(Integer areaCode, int limit) {
        int area = areaCode == null ? 0 : areaCode;
        List<Map<String, Object>> ranked = rankingRepository.findRanking(area, "WEEKLY", THEME_ALL, limit);
        if (!ranked.isEmpty()) return ranked;
        return rankingRepository.findFallbackRecommendations(area, limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> rankOf(Long placeId, String period) {
        return rankingRepository.findRankOf(placeId, normalizePeriod(period), THEME_ALL);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> popularTags(int areaCode, int limit) {
        return rankingRepository.findPopularTags(areaCode, limit);
    }

    private static String normalizePeriod(String raw) {
        if (raw == null) return "WEEKLY";
        String p = raw.trim().toUpperCase();
        return switch (p) {
            case "DAILY", "WEEKLY", "MONTHLY", "ALL_TIME" -> p;
            default -> "WEEKLY";
        };
    }

    public record RankingResponse(int areaCode, String period, String theme,
                                  List<Map<String, Object>> items) {}
}
