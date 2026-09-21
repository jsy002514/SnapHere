package com.snaphere.api.report;

/** 신고 처리 상태. (SYS-017) */
public enum ReportStatus {

    /** 접수됨. 운영자 검토 대기. */
    PENDING,

    /** 운영자가 조치해 해결했다. {@code action} 에 무엇을 했는지 남는다. */
    RESOLVED,

    /** 운영자가 신고를 기각했다. */
    REJECTED
}
