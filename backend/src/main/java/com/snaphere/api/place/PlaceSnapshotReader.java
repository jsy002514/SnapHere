package com.snaphere.api.place;

import java.util.Optional;

/** 장소 조회 포트. DB(JPA)가 들어오면 이 인터페이스의 구현만 바꾼다. */
public interface PlaceSnapshotReader {

    Optional<PlaceSnapshot> findById(long placeId);
}
