package com.snaphere.api.place;

/** 장소 종류. 기본 인증 반경이 다르다. (PLC-022, PST-027) */
public enum PlaceType {

    /** 관광공사 TourAPI 관광지. 기본 반경 500m */
    OFFICIAL(500),

    /** 사용자가 등록한 숨은 명소. 기본 반경 100m */
    USER(100);

    private final int defaultVerifyRadiusM;

    PlaceType(int defaultVerifyRadiusM) {
        this.defaultVerifyRadiusM = defaultVerifyRadiusM;
    }

    public int defaultVerifyRadiusM() {
        return defaultVerifyRadiusM;
    }
}
