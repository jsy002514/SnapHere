package com.snaphere.api.place;

import java.util.Optional;

/**
 * 시도별 기본 이벤트 인증 반경 조회 포트. (PLC-022)
 * {@code regions.default_event_verify_radius_m} 에 대응한다.
 */
public interface RegionRadiusReader {

    Optional<Integer> defaultEventVerifyRadiusM(int areaCode);
}
