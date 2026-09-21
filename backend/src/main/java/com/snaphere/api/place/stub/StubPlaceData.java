package com.snaphere.api.place.stub;

import com.snaphere.api.place.EventSnapshot;
import com.snaphere.api.place.EventSnapshotReader;
import com.snaphere.api.place.PlaceSnapshot;
import com.snaphere.api.place.PlaceSnapshotReader;
import com.snaphere.api.place.PlaceType;
import com.snaphere.api.place.RegionRadiusReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 로컬 개발용 고정 데이터.
 *
 * <p><b>DB(PostgreSQL + PostGIS)와 JPA 가 들어오면 이 클래스는 삭제한다.</b>
 * TourAPI 적재(PLC-003)와 장소 조회가 없는 상태에서도 등급 판정(API-PST-002)을
 * 앱과 함께 시험해 볼 수 있게 두는 임시 데이터다.
 *
 * <p>{@code snaphere.stub-data=false} 로 끌 수 있다.
 */
@Configuration
@ConditionalOnProperty(prefix = "snaphere", name = "stub-data", matchIfMissing = true)
public class StubPlaceData {

    private static final Map<Long, PlaceSnapshot> PLACES = new LinkedHashMap<>();
    private static final Map<Long, EventSnapshot> EVENTS = new LinkedHashMap<>();
    private static final Map<Integer, Integer> REGION_EVENT_RADIUS = new LinkedHashMap<>();

    static {
        // 관광지 — 기본 반경 500m (PST-027)
        PLACES.put(1L, new PlaceSnapshot(1L, PlaceType.OFFICIAL, 37.579617, 126.977041, true, 500, 1));
        // 사용자 등록 장소 — 기본 반경 100m (PST-027)
        PLACES.put(2L, new PlaceSnapshot(2L, PlaceType.USER, 37.556000, 126.923500, true, 100, 1));
        // 좌표 없는 장소 — 판정 제외 대상 (PLC-007)
        PLACES.put(3L, new PlaceSnapshot(3L, PlaceType.OFFICIAL, null, null, false, 500, 37));
        // 축제 장소 (전북)
        PLACES.put(4L, new PlaceSnapshot(4L, PlaceType.OFFICIAL, 35.791700, 127.425300, true, 500, 37));

        // 이벤트별 반경이 없는 행사 → 지역 기본값으로 내려간다 (EVT-023)
        EVENTS.put(1L, new EventSnapshot(1L, null, 37, 4L));
        // 이벤트별 반경이 지정된 행사 → 이 값이 최우선 (PLC-022)
        EVENTS.put(2L, new EventSnapshot(2L, 3_000, 1, 1L));

        // 전북은 지역 기본값을 2,500m 로 재정의해 둔 상태 (PLC-022)
        REGION_EVENT_RADIUS.put(37, 2_500);
    }

    @Bean
    public PlaceSnapshotReader stubPlaceSnapshotReader() {
        return placeId -> Optional.ofNullable(PLACES.get(placeId));
    }

    @Bean
    public EventSnapshotReader stubEventSnapshotReader() {
        return eventId -> Optional.ofNullable(EVENTS.get(eventId));
    }

    @Bean
    public RegionRadiusReader stubRegionRadiusReader() {
        return areaCode -> Optional.ofNullable(REGION_EVENT_RADIUS.get(areaCode));
    }
}
