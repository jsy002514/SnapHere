package com.ssafy.snaphere.domain.place.repository;

import com.ssafy.snaphere.domain.place.entity.PlaceImage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, Long> {

    /** 정렬에 PK 를 붙여 동일 sortOrder 구간의 순서를 고정한다(tie-breaker). */
    List<PlaceImage> findByPlaceIdOrderBySortOrderAscIdAsc(Long placeId, Pageable pageable);
}
