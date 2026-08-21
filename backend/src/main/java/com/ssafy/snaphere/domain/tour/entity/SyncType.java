package com.ssafy.snaphere.domain.tour.entity;

/** sync_logs.sync_type — DDL 의 허용값 주석과 반드시 일치해야 한다. */
public enum SyncType {
    TOUR_API_AREA, TOUR_API_DETAIL, TOUR_API_IMAGE, TOUR_API_FESTIVAL,
    RANKING, HEATMAP, STATS, COUNTER_FIX, PURGE, POPULARITY, TAG_STATS
}
