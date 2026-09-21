package com.snaphere.api.place;

import java.util.Optional;

/** 이벤트 조회 포트. */
public interface EventSnapshotReader {

    Optional<EventSnapshot> findById(long eventId);
}
