package com.snaphere.api.post;

/** 게시글 노출 상태. 삭제는 논리 삭제다. (PST-043) */
public enum PostStatus {

    ACTIVE,

    /** 신고 처리로 가려진 게시글. 작성자에게만 보인다. */
    HIDDEN,

    /** 작성자가 삭제. 방문 기록과 뱃지는 유지된다 (VST 는 posts 에서 파생하지 않는다). */
    DELETED
}
