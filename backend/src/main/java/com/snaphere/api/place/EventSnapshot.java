package com.snaphere.api.place;

/**
 * 등급 판정에 필요한 이벤트 정보만 담은 읽기 전용 뷰.
 *
 * @param verifyRadiusM null 이면 지역 기본값, 그것도 없으면 2,000m 를 쓴다 (EVT-023, PLC-022)
 */
public record EventSnapshot(
        long eventId,
        Integer verifyRadiusM,
        int areaCode,
        Long placeId
) {
}
