package com.snaphere.api.report;

/**
 * 운영자 검토 결과. (SYS-017)
 *
 * <p>검토 전에는 null 이다. 상태와 짝이 맞아야 하므로 DB 에서도 CHECK 로 묶었다.
 */
public enum ReportAction {

    /** 문제없음. 자동 블라인드된 게시글이면 복구한다. */
    KEEP,

    /** 가림. 작성자에게만 보인다. */
    HIDE,

    /** 삭제. */
    DELETE
}
