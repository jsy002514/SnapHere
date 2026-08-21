package com.ssafy.snaphere.domain.heatmap.controller;

import com.ssafy.snaphere.domain.heatmap.dto.HeatmapDtos.*;
import com.ssafy.snaphere.domain.heatmap.service.HeatmapService;
import com.ssafy.snaphere.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "지도 - 히트맵")
@RestController
@RequestMapping("/api/v1/map")
public class HeatmapController {

    private final HeatmapService heatmapService;

    public HeatmapController(HeatmapService heatmapService) { this.heatmapService = heatmapService; }

    @Operation(summary = "히트맵 격자 ★ 메인 화면",
               description = "nextRefreshAt 이후에만 재조회한다. 웹소켓·SSE 를 쓰지 않는 대신 서버가 폴링 주기를 통제한다.")
    @GetMapping("/heatmap")
    public ApiResponse<HeatmapResponse> heatmap(
            @RequestParam double swLat, @RequestParam double swLng,
            @RequestParam double neLat, @RequestParam double neLng,
            @RequestParam int zoom,
            @RequestParam(required = false, defaultValue = "REALTIME") String period) {
        return ApiResponse.ok(heatmapService.heatmap(swLat, swLng, neLat, neLng, zoom, period));
    }

    @Operation(summary = "시도별 활동량 (지도 축소 상태)",
               description = "labelLat/labelLng 는 터치 타겟용 좌표다. 얇고 긴 지역은 영역 탭이 어렵다.")
    @GetMapping("/regions")
    public ApiResponse<RegionActivityResponse> regions(
            @RequestParam(required = false, defaultValue = "REALTIME") String period) {
        return ApiResponse.ok(heatmapService.regionActivity(period));
    }

    @Operation(summary = "인기 사진 레이어",
               description = "썸네일 URL 은 집계 단계에서 미리 저장한다. 조회 시 조인하면 지도 드래그마다 무거워진다.")
    @GetMapping("/photo-markers")
    public ApiResponse<PhotoMarkerResponse> photoMarkers(
            @RequestParam double swLat, @RequestParam double swLng,
            @RequestParam double neLat, @RequestParam double neLng,
            @RequestParam int zoom,
            @RequestParam(required = false, defaultValue = "REALTIME") String period) {
        return ApiResponse.ok(heatmapService.photoMarkers(swLat, swLng, neLat, neLng, zoom, period));
    }
}
