package com.snaphere.api.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 시도 마스터. (PLC-001)
 *
 * <p>시도 코드는 비연속이다(1~8, 31~39). 순번을 매기지 않고 TourAPI 의 areaCode 를 그대로 PK 로 쓴다.
 */
@Entity
@Table(name = "regions")
public class RegionEntity {

    @Id
    @Column(name = "area_code")
    private Integer areaCode;

    @Column(name = "name_ko", nullable = false, length = 50)
    private String nameKo;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(name = "representative_image_url", length = 2048)
    private String representativeImageUrl;

    /** 행사 인증 반경 지역 기본값. null 이면 2,000m 를 쓴다. (PLC-022) */
    @Column(name = "default_event_verify_radius_m")
    private Integer defaultEventVerifyRadiusM;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RegionEntity() {
    }

    public Integer getAreaCode() {
        return areaCode;
    }

    public String getNameKo() {
        return nameKo;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getRepresentativeImageUrl() {
        return representativeImageUrl;
    }

    public Integer getDefaultEventVerifyRadiusM() {
        return defaultEventVerifyRadiusM;
    }
}
