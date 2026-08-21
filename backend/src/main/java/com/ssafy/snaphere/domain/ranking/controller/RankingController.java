package com.ssafy.snaphere.domain.ranking.controller;

import com.ssafy.snaphere.domain.ranking.service.RankingService;
import com.ssafy.snaphere.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "랭킹 · 추천")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @Operation(summary = "장소 랭킹",
               description = "areaCode 를 비우면 전국. previous_rank 로 순위 변동(상승·하락·NEW)을 표시할 수 있다.")
    @GetMapping("/rankings/places")
    public ApiResponse<RankingService.RankingResponse> places(
            @RequestParam(required = false) Integer areaCode,
            @RequestParam(required = false, defaultValue = "WEEKLY") String period,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(rankingService.places(areaCode, period, Math.min(100, Math.max(1, limit))));
    }

    @Operation(summary = "추천 장소",
               description = "랭킹이 비어 있으면 featured·인기순 fallback 으로 채운다. 빈 화면을 주지 않는다.")
    @GetMapping("/recommendations/places")
    public ApiResponse<List<Map<String, Object>>> recommendations(
            @RequestParam(required = false) Integer areaCode,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(rankingService.recommendations(areaCode, Math.min(50, Math.max(1, limit))));
    }
}
