package com.ssafy.snaphere.domain.event.controller;

import com.ssafy.snaphere.domain.place.dto.PlaceDtos.NearbyResponse;
import com.ssafy.snaphere.domain.place.entity.Place;
import com.ssafy.snaphere.domain.place.repository.PlaceRepository;
import com.ssafy.snaphere.domain.place.service.PlaceService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.common.PageResponse;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 이벤트 탭 — 지자체 행사·축제.
 *
 * 행사는 별도 테이블이 아니라 places 의 content_type_id = 15 다.
 * 장소와 같은 테이블에 두면 주변 검색·히트맵·게시물 연결이 전부 그대로 동작한다.
 */
@Tag(name = "이벤트 (지자체 행사)")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final PlaceRepository placeRepository;
    private final PlaceService placeService;

    @Operation(summary = "행사 목록 (진행중·예정)",
               description = "종료일이 오늘 이후인 행사만. 시작일 오름차순이라 임박한 행사가 먼저 나온다.")
    @GetMapping
    public ApiResponse<PageResponse<EventItem>> list(
            @RequestParam(required = false) Integer areaCode,
            @Valid PageRequestParam pageParam) {
        var page = placeRepository.findEvents(LocalDate.now(), areaCode, pageParam.toPageable());
        return ApiResponse.ok(PageResponse.from(page, EventItem::from));
    }

    @Operation(summary = "주변 행사",
               description = "장소 주변 검색을 contentTypeId=15 로 재사용한다. 별도 쿼리를 만들지 않았다.")
    @GetMapping("/nearby")
    public ApiResponse<NearbyResponse> nearby(@RequestParam double lat, @RequestParam double lng,
                                              @RequestParam(required = false) Integer radius,
                                              @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(placeService.nearby(lat, lng,
                radius == null ? 10000 : radius, null, 15, null, limit));
    }

    @Operation(summary = "행사 상세")
    @GetMapping("/{placeId}")
    public ApiResponse<EventItem> detail(@PathVariable Long placeId) {
        Place p = placeRepository.findById(placeId)
                .filter(Place::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_001));
        return ApiResponse.ok(EventItem.from(p));
    }

    public record EventItem(
            Long placeId, String title, String addr1, String thumbnailUrl,
            Integer areaCode, java.math.BigDecimal lat, java.math.BigDecimal lng,
            LocalDate startDate, LocalDate endDate, String eventPlace, String organizer,
            boolean ongoing, Integer postCount) {

        public static EventItem from(Place p) {
            return new EventItem(p.getId(), p.getTitle(), p.getAddr1(), p.getFirstImageThumb(),
                    p.getAreaCode(), p.getLat(), p.getLng(),
                    p.getEventStartDate(), p.getEventEndDate(), p.getEventPlace(), p.getOrganizer(),
                    p.isEventOngoing(LocalDate.now()), p.getPostCount());
        }
    }
}
