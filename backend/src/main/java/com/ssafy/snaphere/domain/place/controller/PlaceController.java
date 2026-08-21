package com.ssafy.snaphere.domain.place.controller;

import com.ssafy.snaphere.domain.place.dto.PlaceDtos.*;
import com.ssafy.snaphere.domain.place.service.PlaceService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.common.PageResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "장소")
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) { this.placeService = placeService; }

    @Operation(summary = "장소 목록 (지역별, 인기순)")
    @GetMapping
    public ApiResponse<PageResponse<PlaceListItem>> list(
            @RequestParam Integer areaCode,
            @RequestParam(required = false) Integer contentTypeId,
            @RequestParam(required = false) String placeType,
            @Valid PageRequestParam pageParam) {
        return ApiResponse.ok(placeService.list(areaCode, contentTypeId, placeType, pageParam));
    }

    @Operation(summary = "주변 장소 ★ 업로드 장소 후보·상세의 주변·지도 탐색이 모두 이 API 를 쓴다",
               description = "결과가 비어도 nearestDistanceMeters 를 돌려주므로 \"가장 가까운 장소가 3.2km\" 안내가 가능하다.")
    @GetMapping("/nearby")
    public ApiResponse<NearbyResponse> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String placeType,
            @RequestParam(required = false) Integer contentTypeId,
            @RequestParam(required = false) Long excludePlaceId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(placeService.nearby(lat, lng, radius, placeType,
                contentTypeId, excludePlaceId, limit));
    }

    @Operation(summary = "장소 상세")
    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponse> detail(
            @PathVariable Long placeId,
            @AuthUser(required = false) LoginUser me) {
        return ApiResponse.ok(placeService.detail(placeId, me == null ? null : me.userId()));
    }

    @Operation(summary = "사용자 장소 생성 (숨은 명소)",
               description = "반경 100m 안에 같은 이름이 있으면 새로 만들지 않고 기존 장소를 merged=true 로 돌려준다.")
    @PostMapping
    public ApiResponse<PlaceCreateResponse> create(
            @AuthUser LoginUser me,
            @Valid @RequestBody PlaceCreateRequest req) {
        return ApiResponse.ok(placeService.createUserPlace(me.userId(), req));
    }
}
