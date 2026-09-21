package com.snaphere.api.reaction;

/**
 * 좋아요 대상. (PST-040, CMU-018)
 *
 * <p>게시글과 댓글을 한 테이블에 담는다. 나누면 "내가 누른 좋아요" 목록이 UNION 이 되고,
 * 대상이 늘 때마다 테이블이 하나씩 생긴다.
 */
public enum LikeTargetType {

    POST,

    /** 댓글 좋아요는 Could Have 범위다 (CMU-018). 테이블은 미리 받아 둔다. */
    COMMENT
}
