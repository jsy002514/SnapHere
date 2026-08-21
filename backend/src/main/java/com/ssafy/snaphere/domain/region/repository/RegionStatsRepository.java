package com.ssafy.snaphere.domain.region.repository;

import com.ssafy.snaphere.domain.region.entity.RegionStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionStatsRepository extends JpaRepository<RegionStats, Integer> {
}
