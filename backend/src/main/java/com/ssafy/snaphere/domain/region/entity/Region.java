package com.ssafy.snaphere.domain.region.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 17개 시도. PK 는 TourAPI 의 areaCode 를 그대로 쓴다 (1~8, 31~39 — 연속되지 않음). */
@Getter
@Entity
@Table(name = "regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @Column(name = "area_code")
    private Integer areaCode;

    @Column(name = "name_ko", nullable = false, length = 50) private String nameKo;
    @Column(name = "name_en", nullable = false, length = 50) private String nameEn;
    @Column(name = "name_ja", length = 50) private String nameJa;
    @Column(name = "name_zh", length = 50) private String nameZh;

    @Column(name = "center_lat", nullable = false, precision = 10, scale = 7) private BigDecimal centerLat;
    @Column(name = "center_lng", nullable = false, precision = 10, scale = 7) private BigDecimal centerLng;

    @Column(name = "default_zoom", nullable = false) private Integer defaultZoom;

    /**
     * 지도 탭 타겟용 좌표. 충청북도처럼 얇고 긴 지역은 영역을 누르기 어려워
     * 앱이 이 지점에 별도 핀을 놓는다. 값이 없으면 중심점을 그대로 쓴다.
     */
    @Column(name = "label_lat", precision = 10, scale = 7) private BigDecimal labelLat;
    @Column(name = "label_lng", precision = 10, scale = 7) private BigDecimal labelLng;

    public BigDecimal labelLatOrCenter() { return labelLat != null ? labelLat : centerLat; }
    public BigDecimal labelLngOrCenter() { return labelLng != null ? labelLng : centerLng; }
    @Column(name = "thumbnail_url", length = 500)    private String thumbnailUrl;
    @Column(name = "sort_order", nullable = false)   private Integer sortOrder;
}
