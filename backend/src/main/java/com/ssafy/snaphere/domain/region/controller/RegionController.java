package com.ssafy.snaphere.domain.region.controller;

import com.ssafy.snaphere.domain.region.service.RegionService;
import com.ssafy.snaphere.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "지역 · 커뮤니티")
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @Operation(summary = "17개 시도 목록", description = "S1 검증용 엔드포인트이기도 하다. 통계와 라벨 좌표를 포함한다.")
    @GetMapping
    public ApiResponse<List<RegionService.RegionItem>> list() {
        return ApiResponse.ok(regionService.listAll());
    }

    @Operation(summary = "커뮤니티 홈 ★ 한 화면 = 한 번의 호출",
               description = "인기글·추천장소·랭킹·태그를 한 번에 준다. 나눠 부르면 진입이 느려진다.")
    @GetMapping("/{areaCode}/community")
    public ApiResponse<RegionService.CommunityHome> community(@PathVariable int areaCode) {
        return ApiResponse.ok(regionService.community(areaCode));
    }

    @Operation(summary = "지역 인기 태그", description = "배치가 미리 만든 집계(region_tag_stats)를 읽는다")
    @GetMapping("/{areaCode}/tags")
    public ApiResponse<List<Map<String, Object>>> tags(@PathVariable int areaCode,
                                                        @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(regionService.tags(areaCode, Math.min(100, Math.max(1, limit))));
    }
}
