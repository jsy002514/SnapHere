package com.snaphere.api.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 시군구 마스터. areaCode 오퍼레이션으로 적재한다. (PLC-002, PLC-020) */
@Entity
@Table(name = "sigungu")
public class SigunguEntity {

    @EmbeddedId
    private SigunguId id;

    @Column(name = "name_ko", nullable = false, length = 50)
    private String nameKo;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    protected SigunguEntity() {
    }

    public SigunguId getId() {
        return id;
    }

    public String getNameKo() {
        return nameKo;
    }

    public String getNameEn() {
        return nameEn;
    }
}
