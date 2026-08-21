package com.ssafy.snaphere.domain.visit.service;

import com.ssafy.snaphere.domain.place.entity.Place;
import com.ssafy.snaphere.domain.place.repository.PlaceRepository;
import com.ssafy.snaphere.domain.place.repository.PlaceWriteRepository;
import com.ssafy.snaphere.domain.post.entity.PostSource;
import com.ssafy.snaphere.domain.post.entity.PostTier;
import com.ssafy.snaphere.domain.post.service.TierEvaluator;
import com.ssafy.snaphere.domain.visit.repository.VisitRepository;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import com.ssafy.snaphere.global.util.GeoUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방문 기록.
 *
 * 체크인은 게시물 없이 "여기 왔다" 만 남기는 기능이다.
 * 사진을 올리지 않아도 방문 지도를 채울 수 있어야 하지만,
 * 좌표 검증 없이 허용하면 집에서 전국을 다 방문할 수 있으므로 인증 반경을 반드시 검사한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitRepository visitRepository;
    private final PlaceRepository placeRepository;
    private final PlaceWriteRepository placeWriteRepository;

    @Value("${app.tier.on-site-window-minutes}")   private int onSiteWindowMinutes;
    @Value("${app.tier.location-confirmed-days}")  private int locationConfirmedDays;

    @Transactional
    public CheckinResult checkin(Long userId, Long placeId, double lat, double lng) {
        if (!GeoUtils.isInKorea(lat, lng)) throw new BusinessException(ErrorCode.PLACE_003);

        Place place = placeRepository.findById(placeId)
                .filter(Place::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_001));
        if (!place.isHasCoordinateSafe()) throw new BusinessException(ErrorCode.PLACE_003);

        int distance = (int) Math.round(GeoUtils.distanceMeters(
                lat, lng, place.getLat().doubleValue(), place.getLng().doubleValue()));

        // 체크인은 "지금 여기 있다" 는 행위이므로 촬영시각을 현재로 두고 CAMERA 와 동일하게 평가한다.
        TierEvaluator.Result r = TierEvaluator.evaluate(distance, place.getVerifyRadiusM(),
                PostSource.CAMERA, LocalDateTime.now(), LocalDateTime.now(),
                onSiteWindowMinutes, locationConfirmedDays);

        if (!r.createsVisit()) throw new BusinessException(ErrorCode.PLACE_004);

        LocalDate today = LocalDate.now();
        boolean created = visitRepository.record(userId, placeId, place.getAreaCode(), null,
                "MANUAL_CHECKIN", r.tier().name(), today);
        if (created) placeWriteRepository.addVisitCount(placeId, 1);

        log.info("[CHECKIN] userId={} placeId={} distance={}m tier={} 신규={}",
                userId, placeId, distance, r.tier(), created);

        return new CheckinResult(placeId, distance, r.tier().name(), !created);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myVisits(Long userId, int page, int size) {
        return visitRepository.findMyVisits(userId, size, (page - 1) * size);
    }

    @Transactional(readOnly = true)
    public VisitStats stats(Long userId) {
        Map<String, Object> s = visitRepository.stats(userId);
        return new VisitStats(
                toInt(s.get("place_count")), toInt(s.get("region_count")),
                toInt(s.get("visit_count")), toInt(s.get("on_site_count")),
                s.get("last_visited_at") == null ? null : s.get("last_visited_at").toString(),
                visitRepository.statsByRegion(userId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> visitorsOf(Long placeId, int page, int size) {
        return visitRepository.findVisitorsOfPlace(placeId, size, (page - 1) * size);
    }

    private static int toInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    public record CheckinResult(Long placeId, Integer distanceMeters, String tier,
                                boolean alreadyVisitedToday) {}

    public record VisitStats(int placeCount, int regionCount, int visitCount, int onSiteCount,
                             String lastVisitedAt, List<Map<String, Object>> byRegion) {}
}
