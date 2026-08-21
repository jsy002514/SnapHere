package com.ssafy.snaphere.domain.heatmap.repository;

import com.ssafy.snaphere.domain.heatmap.entity.HeatmapCell;
import com.ssafy.snaphere.domain.heatmap.entity.HeatmapPeriod;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HeatmapCellRepository extends JpaRepository<HeatmapCell, Long> {

    /**
     * 화면 범위 안의 격자. intensity 높은 순으로 상한까지만 준다.
     * bbox 는 lat/lng 컬럼으로 거르는 편이 격자 조회에서는 공간 인덱스보다 단순하고 빠르다
     * (격자 수가 애초에 적고, idx_heatmap_lookup 이 grid_level+period 를 먼저 좁혀준다).
     */
    @Query("""
            SELECT c FROM HeatmapCell c
            WHERE c.gridLevel = :gridLevel AND c.period = :period
              AND c.cellLat BETWEEN :swLat AND :neLat
              AND c.cellLng BETWEEN :swLng AND :neLng
            ORDER BY c.intensity DESC, c.id ASC
            """)
    List<HeatmapCell> findInBounds(@Param("gridLevel") int gridLevel,
                                   @Param("period") HeatmapPeriod period,
                                   @Param("swLat") double swLat, @Param("neLat") double neLat,
                                   @Param("swLng") double swLng, @Param("neLng") double neLng,
                                   Pageable pageable);

    @Query("""
            SELECT COUNT(c) FROM HeatmapCell c
            WHERE c.gridLevel = :gridLevel AND c.period = :period
              AND c.cellLat BETWEEN :swLat AND :neLat
              AND c.cellLng BETWEEN :swLng AND :neLng
            """)
    long countInBounds(@Param("gridLevel") int gridLevel,
                       @Param("period") HeatmapPeriod period,
                       @Param("swLat") double swLat, @Param("neLat") double neLat,
                       @Param("swLng") double swLng, @Param("neLng") double neLng);

    /** 인기 사진 레이어 — 격자별 대표 게시물 썸네일. 조회 시 조인하지 않는다(집계 때 미리 저장). */
    @Query("""
            SELECT c FROM HeatmapCell c
            WHERE c.gridLevel = :gridLevel AND c.period = :period
              AND c.topPostId IS NOT NULL
              AND c.cellLat BETWEEN :swLat AND :neLat
              AND c.cellLng BETWEEN :swLng AND :neLng
            ORDER BY c.intensity DESC, c.id ASC
            """)
    List<HeatmapCell> findPhotoMarkers(@Param("gridLevel") int gridLevel,
                                        @Param("period") HeatmapPeriod period,
                                        @Param("swLat") double swLat, @Param("neLat") double neLat,
                                        @Param("swLng") double swLng, @Param("neLng") double neLng,
                                        Pageable pageable);
}
