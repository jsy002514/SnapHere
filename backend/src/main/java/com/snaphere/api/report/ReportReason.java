package com.snaphere.api.report;

/**
 * 신고 사유. (PST-043)
 *
 * <p>명세의 enum 값을 그대로 쓴다. 표시 문구는 서버가 만들지 않고 앱이 {@link #messageKey()} 로
 * 다국어를 조립한다 (SYS-010, SYS-011).
 */
public enum ReportReason {

    INAPPROPRIATE,
    COPYRIGHT,

    /** 사진이 지정된 장소에서 찍힌 것이 아니다. 위치 신뢰 체계에 직접 걸리는 사유다. */
    PLACE_MISMATCH,
    SPAM,
    OTHER;

    public String messageKey() {
        return "report.reason." + name().toLowerCase();
    }
}
