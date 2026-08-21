package com.ssafy.snaphere.domain.place.controller;

import com.ssafy.snaphere.domain.place.service.PlaceService;
import com.ssafy.snaphere.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 홈(랜딩) 지도용. 히트맵은 집계 테이블을 읽어야 하므로 HeatmapController 로 분리한다.
 */
@Tag(name = "지도")
@RestController
@RequestMapping("/api/v1/map")
public class MapController {

    private final PlaceService placeService;

    public MapController(PlaceService placeService) { this.placeService = placeService; }

    @Operation(summary = "화면 영역 안의 장소 마커 (상위 200개)",
               description = "truncated=true 면 더 확대하라는 안내를 띄운다. 잘린 사실을 숨기지 않는다.")
    @GetMapping("/markers")
    public ApiResponse<PlaceService.MarkersResponse> markers(
            @RequestParam double minLat, @RequestParam double maxLat,
            @RequestParam double minLng, @RequestParam double maxLng,
            @RequestParam(required = false) Integer contentTypeId) {
        return ApiResponse.ok(placeService.markers(minLat, maxLat, minLng, maxLng, contentTypeId));
    }
}
