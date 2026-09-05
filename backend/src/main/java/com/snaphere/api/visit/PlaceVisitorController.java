package com.snaphere.api.visit;

import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.CursorPage;
import com.snaphere.api.common.web.TraceIdFilter;
import com.snaphere.api.post.dto.UserSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-VST-004 — 장소 방문자.
 *
 * <p>기능 명세: 6.1 장소 정보 &gt; 방문자 · 랭킹
 * <p>요구사항: VST-005
 *
 * <p>경로 접두어가 {@code /api/v1/places} 라 발자국 컨트롤러와 갈라 둔다. 장소 상세 화면이
 * 쓰는 값이고, 비회원도 볼 수 있다 (명세 Bearer optional).
 */
@RestController
@RequestMapping("/api/v1/places/{placeId}")
public class PlaceVisitorController {

    private final PlaceVisitorService placeVisitorService;

    public PlaceVisitorController(PlaceVisitorService placeVisitorService) {
        this.placeVisitorService = placeVisitorService;
    }

    @GetMapping("/visitors")
    public ResponseEntity<ApiResponse<CursorPage<UserSummaryResponse>>> visitors(
            @PathVariable long placeId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpRequest) {

        CursorPage<UserSummaryResponse> page =
                placeVisitorService.ofPlace(placeId, cursor, size);

        return ResponseEntity.ok(ApiResponse.ok(page,
                TraceIdFilter.currentTraceId(httpRequest)));
    }
}
