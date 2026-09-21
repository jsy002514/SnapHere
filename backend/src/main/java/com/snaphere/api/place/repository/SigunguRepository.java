package com.snaphere.api.place.repository;

import com.snaphere.api.place.entity.SigunguEntity;
import com.snaphere.api.place.entity.SigunguId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 시군구 마스터 조회. (PLC-002) */
public interface SigunguRepository extends JpaRepository<SigunguEntity, SigunguId> {

    List<SigunguEntity> findByIdAreaCodeOrderByIdSigunguCode(Integer areaCode);
}
