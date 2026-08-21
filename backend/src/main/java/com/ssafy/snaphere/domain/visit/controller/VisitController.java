package com.ssafy.snaphere.domain.visit.controller;

import com.ssafy.snaphere.domain.visit.service.VisitService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "방문 기록")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    public record CheckinRequest(@NotNull BigDecimal lat, @NotNull BigDecimal lng) {}

    @Operation(summary = "체크인",
               description = "인증 반경 밖이면 PLACE_004. 같은 날 같은 장소는 1회만 기록된다.")
    @PostMapping("/places/{placeId}/checkin")
    public ApiResponse<VisitService.CheckinResult> checkin(@PathVariable Long placeId,
                                                           @AuthUser LoginUser me,
                                                           @RequestBody CheckinRequest req) {
        return ApiResponse.ok(visitService.checkin(me.userId(), placeId,
                req.lat().doubleValue(), req.lng().doubleValue()));
    }

    @Operation(summary = "내 방문 기록")
    @GetMapping("/visits")
    public ApiResponse<List<Map<String, Object>>> myVisits(@AuthUser LoginUser me,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(visitService.myVisits(me.userId(),
                Math.max(1, page), Math.min(100, Math.max(1, size))));
    }

    @Operation(summary = "방문 통계 (마이페이지 지도 색칠용)")
    @GetMapping("/visits/stats")
    public ApiResponse<VisitService.VisitStats> stats(@AuthUser LoginUser me) {
        return ApiResponse.ok(visitService.stats(me.userId()));
    }

    @Operation(summary = "장소 방문자 목록")
    @GetMapping("/places/{placeId}/visitors")
    public ApiResponse<List<Map<String, Object>>> visitors(@PathVariable Long placeId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(visitService.visitorsOf(placeId,
                Math.max(1, page), Math.min(100, Math.max(1, size))));
    }
}
