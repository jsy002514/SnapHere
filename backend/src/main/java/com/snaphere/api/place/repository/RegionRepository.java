package com.snaphere.api.place.repository;

import com.snaphere.api.place.entity.RegionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 시도 마스터 조회. (PLC-001) */
public interface RegionRepository extends JpaRepository<RegionEntity, Integer> {
}
