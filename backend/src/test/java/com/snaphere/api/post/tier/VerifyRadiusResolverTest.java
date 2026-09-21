package com.snaphere.api.post.tier;

import com.snaphere.api.place.EventSnapshot;
import com.snaphere.api.place.PlaceSnapshot;
import com.snaphere.api.place.PlaceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 인증 반경 우선순위 — PLC-022, PST-027, EVT-023 */
class VerifyRadiusResolverTest {

    private static final PlaceSnapshot 관광지 =
            new PlaceSnapshot(1L, PlaceType.OFFICIAL, 37.5, 127.0, true, 500, 1);
    private static final PlaceSnapshot 사용자장소 =
            new PlaceSnapshot(2L, PlaceType.USER, 37.5, 127.0, true, 100, 1);

    private VerifyRadiusResolver resolver(Map<Integer, Integer> regionDefaults) {
        return new VerifyRadiusResolver(areaCode -> Optional.ofNullable(regionDefaults.get(areaCode)));
    }

    @Test
    @DisplayName("일반 게시글은 장소에 설정된 반경을 쓴다 — 관광지 500m")
    void 관광지_500() {
        assertThat(resolver(Map.of()).resolve(관광지, null)).isEqualTo(500);
    }

    @Test
    @DisplayName("일반 게시글은 장소에 설정된 반경을 쓴다 — 사용자 장소 100m")
    void 사용자장소_100() {
        assertThat(resolver(Map.of()).resolve(사용자장소, null)).isEqualTo(100);
    }

    @Test
    @DisplayName("이벤트별 반경이 있으면 그것이 최우선이다")
    void 이벤트별_값_우선() {
        EventSnapshot event = new EventSnapshot(1L, 3_000, 37, 1L);

        assertThat(resolver(Map.of(37, 2_500)).resolve(관광지, event)).isEqualTo(3_000);
    }

    @Test
    @DisplayName("이벤트별 반경이 없으면 그 지역 기본값으로 내려간다")
    void 지역_기본값() {
        EventSnapshot event = new EventSnapshot(1L, null, 37, 1L);

        assertThat(resolver(Map.of(37, 2_500)).resolve(관광지, event)).isEqualTo(2_500);
    }

    @Test
    @DisplayName("지역 기본값도 없으면 2,000m 를 쓴다")
    void 최종_폴백_2000() {
        EventSnapshot event = new EventSnapshot(1L, null, 99, 1L);

        assertThat(resolver(Map.of()).resolve(관광지, event))
                .isEqualTo(VerifyRadiusResolver.EVENT_FALLBACK_RADIUS_M)
                .isEqualTo(2_000);
    }

    @Test
    @DisplayName("이벤트 참여글은 장소 반경(500m)을 쓰지 않는다 — 축제장이 넓기 때문")
    void 이벤트는_장소반경_무시() {
        EventSnapshot event = new EventSnapshot(1L, null, 99, 1L);

        assertThat(resolver(Map.of()).resolve(사용자장소, event)).isEqualTo(2_000);
    }
}
