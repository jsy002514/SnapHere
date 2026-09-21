package com.snaphere.api.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** {@code sigungu} 복합 키. TourAPI 의 sigunguCode 는 시도 안에서만 유일하다. */
@Embeddable
public class SigunguId implements Serializable {

    @Column(name = "area_code")
    private Integer areaCode;

    @Column(name = "sigungu_code")
    private Integer sigunguCode;

    protected SigunguId() {
    }

    public SigunguId(Integer areaCode, Integer sigunguCode) {
        this.areaCode = areaCode;
        this.sigunguCode = sigunguCode;
    }

    public Integer getAreaCode() {
        return areaCode;
    }

    public Integer getSigunguCode() {
        return sigunguCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SigunguId other)) {
            return false;
        }
        return Objects.equals(areaCode, other.areaCode)
                && Objects.equals(sigunguCode, other.sigunguCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(areaCode, sigunguCode);
    }
}
