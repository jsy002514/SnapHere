package com.snaphere.api.place.jpa;

import com.snaphere.api.place.RegionRadiusReader;
import com.snaphere.api.place.repository.RegionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@code regions.default_event_verify_radius_m} 를 읽는다. (PLC-022)
 *
 * <p>값이 없으면 {@code Optional.empty()} 를 준다 — 그 다음 단계인 2,000m 기본값 판단은
 * {@code VerifyRadiusResolver} 가 한다. 여기서 기본값을 채워 넣으면 "지역이 정한 값" 과
 * "아무도 정하지 않아 쓰는 값" 을 구분할 수 없다.
 */
@Component
@ConditionalOnProperty(prefix = "snaphere", name = "stub-data", havingValue = "false", matchIfMissing = true)
public class JpaRegionRadiusReader implements RegionRadiusReader {

    private final RegionRepository regions;

    public JpaRegionRadiusReader(RegionRepository regions) {
        this.regions = regions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> defaultEventVerifyRadiusM(int areaCode) {
        return regions.findById(areaCode)
                .map(region -> region.getDefaultEventVerifyRadiusM());
    }
}
