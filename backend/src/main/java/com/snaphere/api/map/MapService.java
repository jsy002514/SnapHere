package com.snaphere.api.map;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.place.PlaceDtos;
import com.snaphere.api.place.PlaceRepository;
import com.snaphere.api.post.PostResponseAssembler;
import com.snaphere.api.post.PostStatus;
import com.snaphere.api.post.dto.PostSummaryResponse;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MapService {
    private static final int MAX_CELLS = 500;
    private static final int ROTATION_INTERVAL_MS = 3000;
    private final MapRepository maps;
    private final MapCache cache;
    private final PostRepository posts;
    private final PostResponseAssembler postResponses;
    private final PlaceRepository places;

    public MapService(MapRepository maps, MapCache cache, PostRepository posts,
                      PostResponseAssembler postResponses, PlaceRepository places) {
        this.maps = maps;
        this.cache = cache;
        this.posts = posts;
        this.postResponses = postResponses;
        this.places = places;
    }

    public MapDtos.HeatmapResult heatmap(double west, double south, double east, double north,
                                         int zoom, String periodValue, boolean forceRefresh) {
        MapDtos.Bounds bounds = bounds(west, south, east, north);
        MapGrid grid = MapGrid.forZoom(zoom);
        MapPeriod requested = MapPeriod.parse(periodValue);
        String cacheKey = requested + ":" + grid.level() + ":" + west + ":" + south + ":" + east + ":" + north;
        if (!forceRefresh) {
            Optional<MapDtos.HeatmapResult> cached = cache.get(cacheKey);
            if (cached.isPresent()) return cached.get();
        }
        MapPeriod effective = requested;
        if (requested == MapPeriod.LAST_1H && maps.viewportPostCount(requested, grid, bounds) < 5) {
            effective = MapPeriod.LAST_24H;
        }
        List<MapRepository.CellRow> rows = maps.cells(effective, grid, bounds, MAX_CELLS + 1);
        boolean truncated = rows.size() > MAX_CELLS;
        if (truncated) rows = rows.subList(0, MAX_CELLS);
        int maxCount = rows.isEmpty() ? 0 : rows.getFirst().postCount();
        List<MapDtos.HeatmapCell> cells = rows.stream().map(row -> cell(row, maxCount)).toList();
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
        MapDtos.HeatmapResult result = new MapDtos.HeatmapResult(cells, maxCount, requested, effective,
                requested != effective, maps.nextRefreshAt(effective, grid, now), truncated);
        cache.put(cacheKey, result);
        return result;
    }

    public List<MapDtos.PhotoMarker> photoMarkers(double west, double south, double east, double north,
                                                   int zoom, String periodValue, Optional<UUID> viewerId) {
        MapDtos.HeatmapResult heatmap = heatmap(west, south, east, north, zoom, periodValue, false);
        MapGrid grid = MapGrid.forZoom(zoom);
        MapDtos.Bounds bounds = bounds(west, south, east, north);
        List<MapRepository.CellRow> rows = maps.cells(heatmap.effectivePeriod(), grid, bounds, MAX_CELLS);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (MapRepository.CellRow row : rows) {
            int limit = zoom >= 14 ? 1 : 10;
            row.samplePostIds().stream().limit(limit).forEach(ids::add);
        }
        Map<Long, PostSummaryResponse> summaries = summaries(ids, viewerId);
        List<MapDtos.PhotoMarker> result = new ArrayList<>();
        for (MapRepository.CellRow row : rows) {
            int limit = zoom >= 14 ? 1 : 10;
            List<PostSummaryResponse> candidates = row.samplePostIds().stream().limit(limit)
                    .map(summaries::get).filter(java.util.Objects::nonNull).toList();
            if (!candidates.isEmpty()) {
                result.add(new MapDtos.PhotoMarker(key(row), row.lat(), row.lng(), candidates, ROTATION_INTERVAL_MS));
            }
        }
        return List.copyOf(result);
    }

    public MapDtos.HeatmapCellDetail cellDetail(String cellKey, Optional<UUID> viewerId) {
        MapCellKey decoded = MapCellKey.decode(cellKey);
        MapRepository.CellRow row = maps.cell(decoded).orElseThrow(() -> new ApiException(ErrorCode.COMMON_404));
        Map<Long, PostSummaryResponse> summaries = summaries(new LinkedHashSet<>(row.samplePostIds()), viewerId);
        List<PostSummaryResponse> samples = row.samplePostIds().stream().map(summaries::get)
                .filter(java.util.Objects::nonNull).toList();
        PlaceDtos.PlaceSummary topPlace = row.topPlaceId() == null ? null
                : places.summary(row.topPlaceId(), viewerId.orElse(null));
        return new MapDtos.HeatmapCellDetail(cell(row, row.postCount()), topPlace, samples, ROTATION_INTERVAL_MS);
    }

    public List<MapDtos.MapRegion> regions(String periodValue, Optional<UUID> viewerId) {
        List<MapRepository.RegionRow> rows = maps.regions(MapPeriod.parse(periodValue));
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        rows.stream().map(MapRepository.RegionRow::representativePostId)
                .filter(java.util.Objects::nonNull).forEach(ids::add);
        Map<Long, PostSummaryResponse> summaries = summaries(ids, viewerId);
        return rows.stream().map(row -> new MapDtos.MapRegion(row.region(), row.postCount(),
                row.contributorCount(), row.representativePostId() == null
                        ? null : summaries.get(row.representativePostId()))).toList();
    }

    private Map<Long, PostSummaryResponse> summaries(LinkedHashSet<Long> ids, Optional<UUID> viewerId) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, PostEntity> byId = new HashMap<>();
        for (PostEntity post : posts.findAllById(ids)) {
            if (post.getStatus() == PostStatus.ACTIVE) byId.put(post.getPostId(), post);
        }
        List<PostEntity> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        List<PostSummaryResponse> assembled = postResponses.summaries(ordered, viewerId);
        Map<Long, PostSummaryResponse> result = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) result.put(ordered.get(i).getPostId(), assembled.get(i));
        return result;
    }

    static MapDtos.Bounds bounds(double west, double south, double east, double north) {
        if (!Double.isFinite(west) || !Double.isFinite(south) || !Double.isFinite(east) || !Double.isFinite(north)
                || west < -180 || east > 180 || south < -90 || north > 90 || west >= east || south >= north) {
            throw new ApiException(ErrorCode.MAP_INVALID_BOUNDS);
        }
        return new MapDtos.Bounds(west, south, east, north);
    }

    private static MapDtos.HeatmapCell cell(MapRepository.CellRow row, int maxCount) {
        double intensity = maxCount == 0 ? 0d : Math.log(row.postCount() + 1d) / Math.log(maxCount + 1d);
        return new MapDtos.HeatmapCell(key(row), row.lat(), row.lng(), row.postCount(), intensity,
                row.visitCount(), row.userCount(), row.topPlaceId() == null ? null : ExternalIds.place(row.topPlaceId()),
                row.samplePostIds().stream().map(ExternalIds::post).toList(), row.lastPostedAt());
    }

    private static String key(MapRepository.CellRow row) {
        return new MapCellKey(row.period(), row.level(), row.latIndex(), row.lngIndex()).encode();
    }
}
