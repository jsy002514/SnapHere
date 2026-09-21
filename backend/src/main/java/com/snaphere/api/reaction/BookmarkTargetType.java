package com.snaphere.api.reaction;

/**
 * 저장 대상. (CMU-023, PLC-015)
 *
 * <p>좋아요와 대상 종류가 다르다 — 좋아요는 게시글·댓글, 저장은 게시글·장소다. 그래서 enum 을
 * 공유하지 않는다. 하나로 합치면 "댓글을 저장한다" 같은 조합이 타입 수준에서 허용된다.
 */
public enum BookmarkTargetType {

    POST,

    /** 장소 저장. 마이페이지 저장함에서 게시글과 함께 모아 본다 (PLC-015). */
    PLACE
}
