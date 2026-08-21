package com.ssafy.snaphere.domain.region.dto;

import com.ssafy.snaphere.domain.region.entity.Region;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public final class RegionDtos {

    private RegionDtos() {}

    @Schema(name = "RegionResponse")
    public record RegionResponse(
            Integer areaCode, String nameKo, String nameEn, String nameJa, String nameZh,
            BigDecimal centerLat, BigDecimal centerLng, Integer defaultZoom, String thumbnailUrl
    ) {
        public static RegionResponse from(Region r) {
            return new RegionResponse(r.getAreaCode(), r.getNameKo(), r.getNameEn(),
                    r.getNameJa(), r.getNameZh(), r.getCenterLat(), r.getCenterLng(),
                    r.getDefaultZoom(), r.getThumbnailUrl());
        }
    }
}
