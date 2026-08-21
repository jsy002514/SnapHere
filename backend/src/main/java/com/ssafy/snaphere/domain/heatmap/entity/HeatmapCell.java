package com.ssafy.snaphere.domain.heatmap.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 히트맵 격자 1칸. 배치가 채우고 조회 API 는 읽기만 한다.
 *
 * ⚠️ geom 은 매핑하지 않는다 — 좌표 쓰기는 nativeQuery 로만 한다(축 순서 사고 방지).
 * ⚠️ intensity 는 서버가 0.0~1.0 으로 정규화해서 준다. 색은 앱이 정한다.
 */
@Getter
@Entity
@Table(name = "heatmap_cells")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HeatmapCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cell_id")
    private Long id;

    @Column(name = "grid_level", nullable = false) private Integer gridLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HeatmapPeriod period;

    @Column(name = "cell_lat", nullable = false, precision = 10, scale = 7) private BigDecimal cellLat;
    @Column(name = "cell_lng", nullable = false, precision = 10, scale = 7) private BigDecimal cellLng;

    @Column(name = "area_code")   private Integer areaCode;
    @Column(name = "post_count",  nullable = false) private int postCount;
    @Column(name = "visit_count", nullable = false) private int visitCount;
    @Column(name = "user_count",  nullable = false) private int userCount;
    @Column(name = "place_count", nullable = false) private int placeCount;

    @Column(nullable = false, precision = 5, scale = 4) private BigDecimal intensity;

    @Column(name = "top_place_id")   private Long topPlaceId;
    @Column(name = "top_post_id")    private Long topPostId;
    @Column(name = "top_post_thumb", length = 500) private String topPostThumb;

    @Column(name = "last_post_at")    private LocalDateTime lastPostAt;
    @Column(name = "calculated_at", nullable = false) private LocalDateTime calculatedAt;
    @Column(name = "next_refresh_at") private LocalDateTime nextRefreshAt;
}
