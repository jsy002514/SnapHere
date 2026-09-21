package com.snaphere.api.place.jpa;

import com.snaphere.api.place.PlaceSnapshot;
import com.snaphere.api.place.PlaceSnapshotReader;
import com.snaphere.api.place.PlaceStatus;
import com.snaphere.api.place.repository.PlaceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@code places} 테이블을 읽는 {@link PlaceSnapshotReader} 구현.
 *
 * <p>숨김·삭제된 장소는 없는 것으로 취급한다 (PLC-023). 그래야 장소가 내려간 뒤에 남은
 * 클라이언트 화면에서 업로드를 시도해도 등급 판정이 아니라 "장소를 찾을 수 없음" 으로 끝난다.
 */
@Component
@ConditionalOnProperty(prefix = "snaphere", name = "stub-data", havingValue = "false", matchIfMissing = true)
public class JpaPlaceSnapshotReader implements PlaceSnapshotReader {

    private final PlaceRepository places;

    public JpaPlaceSnapshotReader(PlaceRepository places) {
        this.places = places;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlaceSnapshot> findById(long placeId) {
        return places.findByPlaceIdAndStatus(placeId, PlaceStatus.ACTIVE)
                .map(place -> place.toSnapshot());
    }
}
