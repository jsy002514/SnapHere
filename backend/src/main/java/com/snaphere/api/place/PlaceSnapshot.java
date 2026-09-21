package com.snaphere.api.place;

/**
 * 등급 판정에 필요한 장소 정보만 담은 읽기 전용 뷰.
 *
 * @param hasCoordinate false 면 주변탐색·히트맵·등급 판정에서 제외한다 (PLC-007)
 * @param verifyRadiusM 이 장소에 적용된 인증 반경. 관리자가 개별 조정할 수 있다 (PLC-022)
 */
public record PlaceSnapshot(
        long placeId,
        PlaceType placeType,
        Double lat,
        Double lng,
        boolean hasCoordinate,
        int verifyRadiusM,
        int areaCode
) {
}
