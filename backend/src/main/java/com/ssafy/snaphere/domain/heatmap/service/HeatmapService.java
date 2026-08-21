package com.ssafy.snaphere.domain.heatmap.service;

import com.ssafy.snaphere.domain.heatmap.dto.HeatmapDtos.*;
import com.ssafy.snaphere.domain.heatmap.entity.HeatmapCell;
import com.ssafy.snaphere.domain.heatmap.entity.HeatmapPeriod;
import com.ssafy.snaphere.domain.heatmap.repository.HeatmapCellRepository;
import com.ssafy.snaphere.domain.place.entity.Place;
import com.ssafy.snaphere.domain.place.repository.PlaceRepository;
import com.ssafy.snaphere.domain.region.entity.Region;
import com.ssafy.snaphere.domain.region.entity.RegionStats;
import com.ssafy.snaphere.domain.region.repository.RegionRepository;
import com.ssafy.snaphere.domain.region.repository.RegionStatsRepository;
import com.ssafy.snaphere.global.util.GeoUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 히트맵 조회. 집계는 배치(HeatmapAggregationService)가 미리 해두고 여기서는 읽기만 한다.
 *
 * 조회 시점에 집계하지 않는 이유: 지도를 드래그할 때마다 posts 전체를 GROUP BY 하면
 * 사용자 수가 조금만 늘어도 지도가 멈춘다. 랜딩 페이지라 가장 많이 호출되는 API 다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeatmapService {

    private final HeatmapCellRepository cellRepository;
    private final RegionRepository regionRepository;
    private final RegionStatsRepository regionStatsRepository;
    private final PlaceRepository placeRepository;

    @Value("${app.heatmap.max-cells}")           private int maxCells;
    @Value("${app.heatmap.fallback-min-cells}")  private int fallbackMinCells;
    @Value("${app.heatmap.realtime-cache-seconds}") private int cacheSeconds;

    @Transactional(readOnly = true)
    public HeatmapResponse heatmap(double swLat, double swLng, double neLat, double neLng,
                                   int zoom, String periodRaw) {
        int gridLevel = GeoUtils.gridLevelOf(zoom);
        HeatmapPeriod requested = HeatmapPeriod.of(periodRaw);

        // 데이터가 적으면 자동으로 기간을 넓힌다.
        // 시연 시작 직후처럼 최근 1시간에 아무것도 없을 때 지도가 텅 비는 것을 막는다.
        HeatmapPeriod effective = requested;
        List<HeatmapCell> cells = load(gridLevel, effective, swLat, swLng, neLat, neLng);
        boolean fallbackApplied = false;

        while (cells.size() < fallbackMinCells && effective != HeatmapPeriod.ALL) {
            HeatmapPeriod wider = effective.widen();
            if (wider == effective) break;
            effective = wider;
            cells = load(gridLevel, effective, swLat, swLng, neLat, neLng);
            fallbackApplied = true;
        }

        long total = cellRepository.countInBounds(gridLevel, effective, swLat, neLat, swLng, neLng);

        // 툴팁용 대표 장소 이름 — 격자 목록의 place id 를 한 번에 조회한다(격자당 1쿼리 금지).
        Map<Long, Place> topPlaces = loadTopPlaces(cells);

        List<CellItem> items = new ArrayList<>(cells.size());
        for (HeatmapCell c : cells) {
            TopPlaceBrief brief = null;
            Place p = c.getTopPlaceId() == null ? null : topPlaces.get(c.getTopPlaceId());
            if (p != null) brief = new TopPlaceBrief(p.getId(), p.getTitle(), p.getFirstImageThumb());
            items.add(CellItem.from(c, brief));
        }

        LocalDateTime calculatedAt = cells.isEmpty() ? null : cells.get(0).getCalculatedAt();
        LocalDateTime nextRefreshAt = cells.isEmpty()
                ? LocalDateTime.now().plusSeconds(cacheSeconds)
                : cells.get(0).getNextRefreshAt();

        return new HeatmapResponse(
                requested.name(), gridLevel,
                fallbackApplied, fallbackApplied ? effective.name() : null,
                calculatedAt, nextRefreshAt,
                total > maxCells, items);
    }

    private List<HeatmapCell> load(int gridLevel, HeatmapPeriod period,
                                   double swLat, double swLng, double neLat, double neLng) {
        return cellRepository.findInBounds(gridLevel, period, swLat, neLat, swLng, neLng,
                PageRequest.of(0, maxCells));
    }

    private Map<Long, Place> loadTopPlaces(List<HeatmapCell> cells) {
        List<Long> ids = cells.stream().map(HeatmapCell::getTopPlaceId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, Place> map = new HashMap<>();
        placeRepository.findAllById(ids).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    // ── 시도별 활동량 (지도 축소 상태) ──

    @Transactional(readOnly = true)
    public RegionActivityResponse regionActivity(String periodRaw) {
        HeatmapPeriod period = HeatmapPeriod.of(periodRaw);

        Map<Integer, RegionStats> statsMap = new HashMap<>();
        regionStatsRepository.findAll().forEach(s -> statsMap.put(s.getAreaCode(), s));

        List<RegionActivityItem> items = regionRepository.findAllByOrderBySortOrderAsc().stream()
                .map(r -> toActivityItem(r, statsMap.get(r.getAreaCode()), period))
                .toList();

        return new RegionActivityResponse(period.name(), items);
    }

    private RegionActivityItem toActivityItem(Region r, RegionStats s, HeatmapPeriod period) {
        int postCount = 0, contributor = 0, placeCount = 0;
        BigDecimal intensity = BigDecimal.ZERO;
        LocalDateTime lastPostAt = null;

        if (s != null) {
            placeCount = s.getPlaceCount();
            contributor = s.getContributorCount();
            lastPostAt = s.getLastPostAt();
            intensity = s.getTrafficIntensity() == null ? BigDecimal.ZERO : s.getTrafficIntensity();
            postCount = switch (period) {
                case REALTIME -> s.getRecentPost1h();
                case DAY -> s.getRecentPost24h();
                default -> s.getPostCount();
            };
        }
        return new RegionActivityItem(r.getAreaCode(), r.getNameKo(), r.getNameEn(),
                r.getCenterLat(), r.getCenterLng(),
                r.labelLatOrCenter(), r.labelLngOrCenter(),
                postCount, contributor, placeCount, intensity, lastPostAt);
    }

    // ── 인기 사진 레이어 ──

    @Transactional(readOnly = true)
    public PhotoMarkerResponse photoMarkers(double swLat, double swLng, double neLat, double neLng,
                                            int zoom, String periodRaw) {
        int gridLevel = GeoUtils.gridLevelOf(zoom);
        HeatmapPeriod period = HeatmapPeriod.of(periodRaw);

        List<HeatmapCell> cells = cellRepository.findPhotoMarkers(gridLevel, period,
                swLat, neLat, swLng, neLng, PageRequest.of(0, maxCells));

        List<PhotoMarker> markers = cells.stream()
                .map(c -> new PhotoMarker(c.getTopPostId(), c.getCellLat(), c.getCellLng(),
                        c.getTopPostThumb(), c.getPostCount(), c.getLastPostAt()))
                .toList();

        long total = cellRepository.countInBounds(gridLevel, period, swLat, neLat, swLng, neLng);
        return new PhotoMarkerResponse(gridLevel, markers, total > maxCells);
    }
}
