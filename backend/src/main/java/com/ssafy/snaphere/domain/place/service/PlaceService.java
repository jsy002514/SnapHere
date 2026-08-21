package com.ssafy.snaphere.domain.place.service;

import com.ssafy.snaphere.domain.place.dto.PlaceDtos.*;
import com.ssafy.snaphere.domain.place.entity.Place;
import com.ssafy.snaphere.domain.place.entity.PlaceStatus;
import com.ssafy.snaphere.domain.place.entity.PlaceType;
import com.ssafy.snaphere.domain.place.repository.PlaceImageRepository;
import com.ssafy.snaphere.domain.place.repository.PlaceRepository;
import com.ssafy.snaphere.domain.place.repository.PlaceRepository.NearbyRow;
import com.ssafy.snaphere.domain.place.repository.PlaceWriteRepository;
import com.ssafy.snaphere.domain.region.entity.Region;
import com.ssafy.snaphere.domain.region.repository.RegionRepository;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.common.PageResponse;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import com.ssafy.snaphere.global.util.FulltextQuery;
import com.ssafy.snaphere.global.util.GeoUtils;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final int MAX_RADIUS_M = 20_000;
    private static final int MAX_NEARBY_LIMIT = 50;
    private static final int MAX_MARKERS = 200;
    private static final int DETAIL_NEARBY_COUNT = 5;
    private static final int OFFICIAL_IMAGE_COUNT = 10;

    private final PlaceRepository placeRepository;
    private final PlaceImageRepository placeImageRepository;
    private final PlaceWriteRepository placeWriteRepository;
    private final RegionRepository regionRepository;

    @Value("${app.place.daily-create-limit}")   private int dailyCreateLimit;
    @Value("${app.place.duplicate-radius-m}")   private int duplicateRadiusM;
    @Value("${app.tier.user-place-verify-radius-m}") private int userPlaceVerifyRadiusM;

    // ── 주변 검색 (업로드 장소 후보 · 상세의 주변 · 지도 탐색이 모두 이걸 쓴다) ──

    @Transactional(readOnly = true)
    public NearbyResponse nearby(double lat, double lng, Integer radius, String placeType,
                                 Integer contentTypeId, Long excludePlaceId, Integer limit) {
        if (!GeoUtils.isInKorea(lat, lng)) throw new BusinessException(ErrorCode.PLACE_003);

        int r = clamp(radius == null ? 500 : radius, 1, MAX_RADIUS_M);
        int lim = clamp(limit == null ? 5 : limit, 1, MAX_NEARBY_LIMIT);
        String type = normalizePlaceType(placeType);

        List<NearbyItem> items = placeRepository
                .findNearby(lat, lng, r, type, contentTypeId, excludePlaceId, lim)
                .stream().map(NearbyItem::from).toList();

        // 결과가 비어도 "가장 가까운 곳이 N m" 를 알려줘야 사용자가 새 장소 만들기로 넘어갈 수 있다.
        Integer nearest = items.isEmpty()
                ? roundOrNull(placeRepository.findNearestDistanceMeters(lat, lng))
                : items.get(0).distanceMeters();

        return new NearbyResponse(items, nearest);
    }

    // ── 목록 ──

    @Transactional(readOnly = true)
    public PageResponse<PlaceListItem> list(Integer areaCode, Integer contentTypeId,
                                            String placeType, PageRequestParam pageParam) {
        if (areaCode == null) throw new BusinessException(ErrorCode.COMMON_400, "areaCode");

        // 인기순 + PK tie-breaker. PK 를 안 붙이면 동점 구간에서 페이지 경계가 흔들려 중복·누락이 생긴다.
        var pageable = pageParam.toPageable(Sort.by(Sort.Direction.DESC, "postCount"), "id");

        Page<Place> page;
        if (contentTypeId != null) {
            page = placeRepository.findByAreaCodeAndContentTypeIdAndStatus(
                    areaCode, contentTypeId, PlaceStatus.ACTIVE, pageable);
        } else if (normalizePlaceType(placeType) != null) {
            page = placeRepository.findByAreaCodeAndPlaceTypeAndStatus(
                    areaCode, PlaceType.valueOf(placeType.toUpperCase()), PlaceStatus.ACTIVE, pageable);
        } else {
            page = placeRepository.findByAreaCodeAndStatus(areaCode, PlaceStatus.ACTIVE, pageable);
        }
        return PageResponse.from(page, PlaceListItem::from);
    }

    // ── 상세 ──

    @Transactional
    public PlaceDetailResponse detail(Long placeId, Long viewerUserId) {
        Place p = placeRepository.findById(placeId)
                .filter(Place::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_001));

        placeWriteRepository.increaseViewCount(placeId);

        var longText = placeRepository.findLongTexts(placeId);
        String overview = longText.map(PlaceRepository.LongTextRow::getOverview).orElse(null);
        String homepage = longText.map(PlaceRepository.LongTextRow::getHomepage).orElse(null);

        List<OfficialImage> images = placeImageRepository
                .findByPlaceIdOrderBySortOrderAscIdAsc(placeId, PageRequest.of(0, OFFICIAL_IMAGE_COUNT))
                .stream().map(i -> new OfficialImage(i.getImageUrl(), i.getThumbnailUrl())).toList();

        String regionName = regionRepository.findById(p.getAreaCode())
                .map(Region::getNameKo).orElse(null);

        EventInfo event = null;
        if (p.getEventStartDate() != null || p.getEventEndDate() != null) {
            event = new EventInfo(p.getEventStartDate(), p.getEventEndDate(),
                    p.getEventPlace(), p.getOrganizer(), p.isEventOngoing(LocalDate.now()));
        }

        List<NearbyItem> nearbyPlaces = List.of();
        if (p.isHasCoordinateSafe()) {
            nearbyPlaces = placeRepository.findNearby(
                            p.getLat().doubleValue(), p.getLng().doubleValue(),
                            3000, null, null, placeId, DETAIL_NEARBY_COUNT)
                    .stream().map(NearbyItem::from).toList();
        }

        PlaceDetail detail = new PlaceDetail(
                p.getId(), p.getPlaceType().name(), p.getContentId(), p.getContentTypeId(),
                p.getTitle(), p.getAddr1(), p.getAddr2(), p.getTel(),
                homepage, overview,
                p.getLat(), p.getLng(), p.getVerifyRadiusM(),
                new RegionBrief(p.getAreaCode(), regionName), p.getSigunguCode(),
                images, event);

        PlaceStats stats = new PlaceStats(p.getPostCount(), p.getLikeCount(), p.getVisitCount(),
                p.getViewCount() + 1, p.getBookmarkCount());

        // TODO(S11): 북마크·방문 리포지토리 연결 후 실제 값으로 교체
        return new PlaceDetailResponse(detail, stats, false, false, nearbyPlaces);
    }

    // ── 사용자 장소 생성 ──

    /**
     * 중복 방지가 이 메서드의 핵심이다.
     * 없으면 같은 카페가 10개 생겨서 랭킹·히트맵·방문기록이 전부 무의미해진다.
     */
    @Transactional
    public PlaceCreateResponse createUserPlace(Long userId, PlaceCreateRequest req) {
        double lat = req.lat().doubleValue();
        double lng = req.lng().doubleValue();

        if (!GeoUtils.isInKorea(lat, lng)) throw new BusinessException(ErrorCode.PLACE_003);

        // 1) 하루 생성 한도
        var todayStart = LocalDate.now().atStartOfDay();
        if (placeRepository.countCreatedSince(userId, todayStart) >= dailyCreateLimit) {
            throw new BusinessException(ErrorCode.PLACE_002);
        }

        // 2) 반경 안에 같은 이름이 있으면 새로 만들지 않고 그걸 돌려준다
        String title = req.title().trim();
        Long duplicate = placeRepository.findDuplicateByTitleNear(title, lat, lng, duplicateRadiusM);
        if (duplicate != null) {
            Place existing = placeRepository.findById(duplicate).orElseThrow();
            log.info("[PLACE-DEDUP] userId={} title={} 기존 placeId={} 반환", userId, title, duplicate);
            return new PlaceCreateResponse(existing.getId(), existing.getPlaceType().name(),
                    existing.getTitle(), true, duplicate,
                    existing.getAreaCode(), existing.getVerifyRadiusM());
        }

        // 3) area_code 추정
        int areaCode = resolveAreaCode(lat, lng);

        Long placeId = placeWriteRepository.insertUserPlace(
                userId, title, lat, lng, req.addr1(), areaCode, userPlaceVerifyRadiusM);

        log.info("[PLACE-CREATE] userId={} placeId={} title={} areaCode={}", userId, placeId, title, areaCode);
        return new PlaceCreateResponse(placeId, PlaceType.USER.name(), title,
                false, null, areaCode, userPlaceVerifyRadiusM);
    }

    /**
     * 좌표 → area_code.
     *  1순위: 20km 안의 가장 가까운 기존 장소의 area_code (실제 행정구역과 거의 일치)
     *  2순위: 17개 시도 중심점 중 가장 가까운 곳 (바다·신규 개발지 등 주변에 장소가 없을 때)
     */
    int resolveAreaCode(double lat, double lng) {
        Integer byNearest = placeWriteRepository.resolveAreaCodeByNearestPlace(lat, lng);
        if (byNearest != null) return byNearest;

        return regionRepository.findAll().stream()
                .filter(r -> r.getCenterLat() != null && r.getCenterLng() != null)
                .min(Comparator.comparingDouble(r -> GeoUtils.distanceMeters(
                        lat, lng, r.getCenterLat().doubleValue(), r.getCenterLng().doubleValue())))
                .map(Region::getAreaCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_001));
    }

    // ── 지도 마커 ──

    @Transactional(readOnly = true)
    public MarkersResponse markers(double minLat, double maxLat, double minLng, double maxLng,
                                   Integer contentTypeId) {
        long total = placeRepository.countMarkersInBounds(minLat, maxLat, minLng, maxLng, contentTypeId);
        List<NearbyItem> items = placeRepository
                .findMarkersInBounds(minLat, maxLat, minLng, maxLng, contentTypeId, MAX_MARKERS)
                .stream().map(NearbyItem::withoutDistance).toList();
        // 잘렸다는 사실을 숨기지 않는다. 프론트가 "더 확대하세요" 를 띄울 수 있어야 한다.
        return new MarkersResponse(items, total, total > MAX_MARKERS);
    }

    public record MarkersResponse(List<NearbyItem> items, long totalCount, boolean truncated) {}

    // ── 검색 ──

    @Transactional(readOnly = true)
    public List<NearbyItem> search(String keyword, Integer areaCode, int limit) {
        String q = FulltextQuery.toBooleanMode(keyword);
        if (q == null) return List.of();
        return placeRepository.searchByKeyword(q, areaCode, clamp(limit, 1, 50))
                .stream().map(NearbyItem::withoutDistance).toList();
    }

    // 검색어 변환 규칙은 게시물 검색과 공유해야 하므로 GeoUtils 옆의 FulltextQuery 로 옮겼다.
    // 여기 두면 다른 패키지에서 못 쓰거나(package-private) 장소 서비스에 의존하게 된다.

    // ── 헬퍼 ──

    private static String normalizePlaceType(String placeType) {
        if (placeType == null || placeType.isBlank()) return null;
        String upper = placeType.toUpperCase();
        if (!upper.equals("OFFICIAL") && !upper.equals("USER")) {
            throw new BusinessException(ErrorCode.COMMON_400, "placeType");
        }
        return upper;
    }

    static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static Integer roundOrNull(Double d) {
        return d == null ? null : (int) Math.round(d);
    }
}
