package com.ssafy.snaphere.domain.region.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 지역 통계. 배치가 갱신하고 조회는 읽기만 한다. */
@Getter
@Entity
@Table(name = "region_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionStats {

    @Id
    @Column(name = "area_code")
    private Integer areaCode;

    @Column(name = "place_count", nullable = false)       private int placeCount;
    @Column(name = "user_place_count", nullable = false)  private int userPlaceCount;
    @Column(name = "post_count", nullable = false)        private int postCount;
    @Column(name = "image_count", nullable = false)       private int imageCount;
    @Column(name = "contributor_count", nullable = false) private int contributorCount;
    @Column(name = "recent_post_1h", nullable = false)    private int recentPost1h;
    @Column(name = "recent_post_24h", nullable = false)   private int recentPost24h;
    @Column(name = "recent_user_1h", nullable = false)    private int recentUser1h;

    @Column(name = "traffic_intensity", nullable = false, precision = 5, scale = 4)
    private BigDecimal trafficIntensity;

    @Column(name = "last_post_at") private LocalDateTime lastPostAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
