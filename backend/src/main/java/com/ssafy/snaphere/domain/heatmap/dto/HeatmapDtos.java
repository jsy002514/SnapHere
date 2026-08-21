package com.ssafy.snaphere.domain.heatmap.dto;

import com.ssafy.snaphere.domain.heatmap.entity.HeatmapCell;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class HeatmapDtos {

    private HeatmapDtos() {}

    public record TopPlaceBrief(Long placeId, String title, String thumbnailUrl) {}

    @Schema(name = "HeatmapCellItem")
    public record CellItem(
            Long cellId, BigDecimal lat, BigDecimal lng,
            int postCount, int userCount, int placeCount,
            @Schema(description = "0.0~1.0 정규화값. 색 농도로 그대로 사용한다. 서버는 색을 정하지 않는다.")
            BigDecimal intensity,
            LocalDateTime lastPostAt,
            TopPlaceBrief topPlace
    ) {
        public static CellItem from(HeatmapCell c, TopPlaceBrief topPlace) {
            return new CellItem(c.getId(), c.getCellLat(), c.getCellLng(),
                    c.getPostCount(), c.getUserCount(), c.getPlaceCount(),
                    c.getIntensity(), c.getLastPostAt(), topPlace);
        }
    }

    @Schema(name = "HeatmapResponse")
    public record HeatmapResponse(
            String period,
            int gridLevel,
            @Schema(description = "true 면 데이터가 적어 더 넓은 기간으로 대체했다. 앱은 \"최근 24시간 기준\" 같은 라벨을 띄운다.")
            boolean fallbackApplied,
            String fallbackPeriod,
            LocalDateTime calculatedAt,
            @Schema(description = "이 시각 이후에만 재조회한다. 앱이 임의 주기로 폴링하면 서버 부하를 통제할 수 없다.")
            LocalDateTime nextRefreshAt,
            @Schema(description = "격자가 상한을 넘어 잘렸다. 앱은 \"더 확대해보세요\" 힌트를 띄운다.")
            boolean truncated,
            List<CellItem> cells
    ) {}

    @Schema(name = "RegionActivityItem")
    public record RegionActivityItem(
            Integer areaCode, String nameKo, String nameEn,
            BigDecimal centerLat, BigDecimal centerLng,
            @Schema(description = "터치 타겟용 좌표. 얇고 긴 지역은 중심점과 다르다.")
            BigDecimal labelLat, BigDecimal labelLng,
            int postCount, int userCount, int placeCount,
            BigDecimal intensity, LocalDateTime lastPostAt
    ) {}

    @Schema(name = "RegionActivityResponse")
    public record RegionActivityResponse(String period, List<RegionActivityItem> regions) {}

    @Schema(name = "PhotoMarker")
    public record PhotoMarker(
            Long postId, BigDecimal lat, BigDecimal lng,
            String thumbnailUrl, int likeCount, LocalDateTime lastPostAt
    ) {}

    @Schema(name = "PhotoMarkerResponse")
    public record PhotoMarkerResponse(int gridLevel, List<PhotoMarker> markers, boolean truncated) {}
}
