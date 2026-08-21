package com.ssafy.snaphere.domain.place.dto;

import com.ssafy.snaphere.domain.place.entity.Place;
import com.ssafy.snaphere.domain.place.repository.PlaceRepository.NearbyRow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PlaceDtos {

    private PlaceDtos() {}

    // ───────── 목록 / 주변 ─────────

    @Schema(name = "NearbyItem")
    public record NearbyItem(
            Long placeId, String placeType, String title, String thumbnailUrl,
            Integer contentTypeId, BigDecimal lat, BigDecimal lng,
            Integer distanceMeters,
            @Schema(description = "true 면 이 좌표로 올릴 때 현장 인증(ON_SITE) 가능")
            boolean withinVerifyRadius,
            Integer verifyRadiusMeters,
            Integer postCount
    ) {
        public static NearbyItem from(NearbyRow r) {
            int dist = r.getDistanceMeters() == null ? 0 : (int) Math.round(r.getDistanceMeters());
            int radius = r.getVerifyRadiusMeters() == null ? 0 : r.getVerifyRadiusMeters();
            return new NearbyItem(r.getPlaceId(), r.getPlaceType(), r.getTitle(), r.getThumbnailUrl(),
                    r.getContentTypeId(), r.getLat(), r.getLng(), dist,
                    dist <= radius, radius, r.getPostCount());
        }

        /** 거리 개념이 없는 목록(마커·검색)에서는 거리·인증가능 여부를 노출하지 않는다. */
        public static NearbyItem withoutDistance(NearbyRow r) {
            return new NearbyItem(r.getPlaceId(), r.getPlaceType(), r.getTitle(), r.getThumbnailUrl(),
                    r.getContentTypeId(), r.getLat(), r.getLng(), null,
                    false, r.getVerifyRadiusMeters(), r.getPostCount());
        }
    }

    @Schema(name = "NearbyResponse")
    public record NearbyResponse(
            List<NearbyItem> items,
            @Schema(description = "결과가 비어도 반환. \"가장 가까운 장소가 3.2km\" 안내 + 새 장소 만들기 유도에 쓴다")
            Integer nearestDistanceMeters
    ) {}

    @Schema(name = "PlaceListItem")
    public record PlaceListItem(
            Long placeId, String placeType, String title, String addr1,
            String thumbnailUrl, Integer contentTypeId,
            BigDecimal lat, BigDecimal lng,
            Integer postCount, Integer likeCount, Integer visitCount
    ) {
        public static PlaceListItem from(Place p) {
            return new PlaceListItem(p.getId(), p.getPlaceType().name(), p.getTitle(), p.getAddr1(),
                    p.getFirstImageThumb(), p.getContentTypeId(), p.getLat(), p.getLng(),
                    p.getPostCount(), p.getLikeCount(), p.getVisitCount());
        }
    }

    // ───────── 상세 ─────────

    public record RegionBrief(Integer areaCode, String nameKo) {}

    public record OfficialImage(String imageUrl, String thumbnailUrl) {}

    public record EventInfo(LocalDate startDate, LocalDate endDate,
                            String eventPlace, String organizer, boolean ongoing) {}

    public record PlaceStats(int postCount, int likeCount, int visitCount,
                             int viewCount, int bookmarkCount) {}

    @Schema(name = "PlaceDetail")
    public record PlaceDetail(
            Long placeId, String placeType, Long contentId, Integer contentTypeId,
            String title, String addr1, String addr2, String tel,
            String homepage, String overview,
            BigDecimal lat, BigDecimal lng, Integer verifyRadiusMeters,
            RegionBrief region, Integer sigunguCode,
            List<OfficialImage> officialImages,
            EventInfo event
    ) {}

    @Schema(name = "PlaceDetailResponse")
    public record PlaceDetailResponse(
            PlaceDetail place,
            PlaceStats stats,
            boolean isBookmarked,
            boolean hasVisited,
            List<NearbyItem> nearbyPlaces
    ) {}

    // ───────── 사용자 장소 생성 ─────────

    @Schema(name = "PlaceCreateRequest")
    public record PlaceCreateRequest(
            @NotBlank @Size(min = 2, max = 100) String title,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
            @Size(max = 255) String addr1
    ) {}

    @Schema(name = "PlaceCreateResponse")
    public record PlaceCreateResponse(
            Long placeId, String placeType, String title,
            @Schema(description = "true 면 새로 만들지 않고 반경 안의 기존 장소를 돌려준 것")
            boolean merged,
            Long mergedFromExisting,
            Integer areaCode,
            Integer verifyRadiusMeters
    ) {}

    // ───────── 체크인 ─────────

    @Schema(name = "CheckinRequest")
    public record CheckinRequest(
            @NotNull BigDecimal lat,
            @NotNull BigDecimal lng
    ) {}

    @Schema(name = "CheckinResponse")
    public record CheckinResponse(Long placeId, Integer distanceMeters,
                                 String tier, boolean alreadyVisitedToday) {}
}
