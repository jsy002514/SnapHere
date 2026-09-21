package com.snaphere.api.post.tier;

import com.snaphere.api.place.EventSnapshot;
import com.snaphere.api.place.PlaceSnapshot;
import com.snaphere.api.place.RegionRadiusReader;
import org.springframework.stereotype.Component;

/**
 * 적용할 인증 반경을 정한다. (PLC-022, PST-027, EVT-023)
 *
 * <p>우선순위가 정해져 있다.
 * <ol>
 *   <li>이벤트 참여글이면 — 이벤트별 값 → 그 지역의 기본값 → 2,000m</li>
 *   <li>일반 게시글이면 — 장소에 설정된 값 (관광지 500m / 사용자 장소 100m)</li>
 * </ol>
 *
 * <p>축제장은 넓어서 500m 로는 부족한 경우가 많다는 판단으로 이벤트 기본값을 2,000m 로 뒀다
 * (미확정 결정 10).
 */
@Component
public class VerifyRadiusResolver {

    /** 이벤트 인증 반경 최종 폴백. 지역별 설정이 없을 때 쓴다 (EVT-023) */
    public static final int EVENT_FALLBACK_RADIUS_M = 2_000;

    private final RegionRadiusReader regionRadiusReader;

    public VerifyRadiusResolver(RegionRadiusReader regionRadiusReader) {
        this.regionRadiusReader = regionRadiusReader;
    }

    public int resolve(PlaceSnapshot place, EventSnapshot event) {
        if (event == null) {
            return place.verifyRadiusM();
        }
        if (event.verifyRadiusM() != null) {
            return event.verifyRadiusM();
        }
        return regionRadiusReader.defaultEventVerifyRadiusM(event.areaCode())
                .orElse(EVENT_FALLBACK_RADIUS_M);
    }
}
