package com.snaphere.api.map;

import com.snaphere.api.place.PlaceDtos;
import com.snaphere.api.post.dto.PostSummaryResponse;

import java.time.OffsetDateTime;
import java.util.List;

public final class MapDtos {
    private MapDtos() { }

    public record Bounds(double west, double south, double east, double north) { }

    public record HeatmapCell(String cellKey, double lat, double lng, int postCount,
                              double intensity, int visitCount, int userCount,
                              String topPlaceId, List<String> samplePostIds,
                              OffsetDateTime lastPostedAt) { }

    public record HeatmapResult(List<HeatmapCell> cells, int maxCount,
                                MapPeriod requestedPeriod, MapPeriod effectivePeriod,
                                boolean fallbackApplied, OffsetDateTime nextRefreshAt,
                                boolean truncated) { }

    public record PhotoMarker(String cellKey, double lat, double lng,
                              List<PostSummaryResponse> candidates, int rotationIntervalMs) { }

    public record HeatmapCellDetail(HeatmapCell cell, PlaceDtos.PlaceSummary topPlace,
                                    List<PostSummaryResponse> samplePosts,
                                    int rotationIntervalMs) { }

    public record MapRegion(PlaceDtos.Region region, int postCount, int contributorCount,
                            PostSummaryResponse representativePost) { }
}
