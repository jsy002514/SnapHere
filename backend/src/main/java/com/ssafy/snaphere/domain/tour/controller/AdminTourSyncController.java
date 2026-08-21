package com.ssafy.snaphere.domain.tour.controller;

import com.ssafy.snaphere.domain.tour.client.TourApiClient;
import com.ssafy.snaphere.domain.tour.entity.SyncLog;
import com.ssafy.snaphere.domain.tour.entity.SyncType;
import com.ssafy.snaphere.domain.tour.repository.SyncLogRepository;
import com.ssafy.snaphere.domain.tour.service.TourApiSyncService;
import com.ssafy.snaphere.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

/**
 * TourAPI 적재 수동 트리거. SecurityConfig 에서 /api/v1/admin/** 는 ROLE_ADMIN 만 허용한다.
 *
 * 관리자 계정 만들기 (최초 1회):
 *   UPDATE users SET role = 'ADMIN' WHERE user_id = 1;
 */
@Tag(name = "관리자 - TourAPI 적재")
@RestController
@RequestMapping("/api/v1/admin/tour-sync")
@RequiredArgsConstructor
public class AdminTourSyncController {

    private final TourApiSyncService syncService;
    private final TourApiClient client;
    private final SyncLogRepository syncLogRepository;

    public record SyncResultResponse(
            Long syncId, String syncType, String target, String status,
            int createdCount, int updatedCount, String message,
            LocalDateTime startedAt, LocalDateTime finishedAt) {

        static SyncResultResponse from(SyncLog s) {
            return new SyncResultResponse(s.getId(), s.getSyncType().name(), s.getTarget(),
                    s.getStatus().name(), s.getCreatedCount(), s.getUpdatedCount(),
                    s.getMessage(), s.getStartedAt(), s.getFinishedAt());
        }
    }

    public record BudgetResponse(int usedToday, int remaining) {}

    @Operation(summary = "남은 일 호출 예산 확인")
    @GetMapping("/budget")
    public ApiResponse<BudgetResponse> budget() {
        return ApiResponse.ok(new BudgetResponse(client.callsUsedToday(), client.callBudgetRemaining()));
    }

    @Operation(summary = "지역·시군구 마스터 적재 (최초 1회)")
    @PostMapping("/areas")
    public ApiResponse<SyncResultResponse> syncAreas() {
        return ApiResponse.ok(SyncResultResponse.from(syncService.syncAreaCodes()));
    }

    @Operation(summary = "단일 조합 적재 — 먼저 이걸로 파이프라인을 확인할 것 (예: areaCode=1, contentTypeId=12)")
    @PostMapping("/places/one")
    public ApiResponse<SyncResultResponse> syncOne(@RequestParam int areaCode,
                                                   @RequestParam int contentTypeId) {
        return ApiResponse.ok(SyncResultResponse.from(
                syncService.syncOnePlaceCombination(areaCode, contentTypeId)));
    }

    @Operation(summary = "전체 적재 (17개 시도 × 유형 6종). 호출량이 크므로 예산을 먼저 확인할 것")
    @PostMapping("/places/all")
    public ApiResponse<String> syncAll() {
        syncService.syncAllPlaces();
        return ApiResponse.ok("완료. 상세는 GET /api/v1/admin/tour-sync/logs 로 확인하세요.");
    }

    @Operation(summary = "축제·행사 적재 (이벤트 탭 데이터)")
    @PostMapping("/festivals")
    public ApiResponse<SyncResultResponse> syncFestivals() {
        return ApiResponse.ok(SyncResultResponse.from(syncService.syncFestivals()));
    }

    @Operation(summary = "최근 적재 내역")
    @GetMapping("/logs")
    public ApiResponse<List<SyncResultResponse>> logs(
            @RequestParam(defaultValue = "TOUR_API_DETAIL") SyncType syncType,
            @RequestParam(defaultValue = "30") int size) {
        List<SyncResultResponse> body = syncLogRepository
                .findBySyncTypeOrderByStartedAtDescIdDesc(syncType, PageRequest.of(0, Math.min(size, 200)))
                .stream().map(SyncResultResponse::from).toList();
        return ApiResponse.ok(body);
    }
}
