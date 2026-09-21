package com.snaphere.api.place;

/** 장소 노출 상태. (PLC-023) */
public enum PlaceStatus {

    ACTIVE,

    /** 신고 누적·부적절 판정으로 숨긴 장소. 조회에서 제외한다. */
    HIDDEN,

    /** 논리 삭제. 게시글이 남아 있어 물리 삭제하지 않는다. */
    DELETED
}
