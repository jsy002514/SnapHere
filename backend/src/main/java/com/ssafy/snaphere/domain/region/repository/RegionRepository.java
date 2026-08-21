package com.ssafy.snaphere.domain.region.repository;

import com.ssafy.snaphere.domain.region.entity.Region;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Integer> {
    List<Region> findAllByOrderBySortOrderAsc();
}
