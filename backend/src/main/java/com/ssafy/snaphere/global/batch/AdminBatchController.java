package com.ssafy.snaphere.global.batch;

import com.ssafy.snaphere.domain.heatmap.service.HeatmapAggregationService;
import com.ssafy.snaphere.domain.ranking.service.RankingService;
import com.ssafy.snaphere.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 배치 수동 실행. SecurityConfig 에서 /api/v1/admin/** 는 ROLE_ADMIN 만 허용한다.
 * 스케줄을 기다리지 않고 시연 직전에 데이터를 채우는 용도이기도 하다.
 */
@Tag(name = "관리자 - 배치")
@RestController
@RequestMapping("/api/v1/admin/batch")
@RequiredArgsConstructor
public class AdminBatchController {

    private final MaintenanceService maintenanceService;
    private final RankingService rankingService;
    private final HeatmapAggregationService heatmapAggregationService;

    @Operation(summary = "히트맵 즉시 갱신 (시연 직전에 유용)")
    @PostMapping("/heatmap")
    public ApiResponse<Map<String, Object>> heatmap() {
        int cells = heatmapAggregationService.refreshRealtime();
        return ApiResponse.ok(Map.of("cells", cells));
    }

    @Operation(summary = "히트맵 장기 기간 갱신 (DAY/WEEK/MONTH/ALL)")
    @PostMapping("/heatmap/long")
    public ApiResponse<Map<String, Object>> heatmapLong() {
        return ApiResponse.ok(Map.of("cells", heatmapAggregationService.refreshLongPeriods()));
    }

    @Operation(summary = "랭킹 전체 재계산")
    @PostMapping("/rankings")
    public ApiResponse<String> rankings() {
        rankingService.rebuildAll();
        return ApiResponse.ok("완료. GET /api/v1/admin/tour-sync/logs?syncType=RANKING 으로 확인");
    }

    @Operation(summary = "카운터 보정")
    @PostMapping("/fix-counters")
    public ApiResponse<Map<String, Object>> fixCounters() {
        return ApiResponse.ok(Map.of("fixedRows", maintenanceService.fixCounters()));
    }

    @Operation(summary = "게시물·사용자 점수 갱신")
    @PostMapping("/popularity")
    public ApiResponse<Map<String, Object>> popularity() {
        return ApiResponse.ok(Map.of(
                "posts", maintenanceService.refreshPopularityScore(),
                "users", maintenanceService.refreshUserPopularity()));
    }

    @Operation(summary = "야간 유지보수 전체 (보정 + 점수 + 블라인드 + 파기 + FULLTEXT)")
    @PostMapping("/nightly")
    public ApiResponse<String> nightly() {
        maintenanceService.runNightly();
        return ApiResponse.ok("완료. GET /api/v1/admin/tour-sync/logs?syncType=COUNTER_FIX 으로 확인");
    }
}
