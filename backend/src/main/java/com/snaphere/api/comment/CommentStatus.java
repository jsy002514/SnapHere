package com.snaphere.api.comment;

/** 댓글 노출 상태. 삭제는 논리 삭제다. (CMU-017) */
public enum CommentStatus {

    ACTIVE,

    /**
     * 작성자가 삭제. 자식 댓글이 있으면 '삭제된 댓글' 자리표시자로 남는다 — 행을 지우면
     * 자식이 함께 사라져 대화의 앞뒤가 끊긴다 (CMU-017).
     */
    DELETED
}
