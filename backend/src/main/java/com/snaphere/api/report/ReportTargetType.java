package com.snaphere.api.report;

/**
 * 신고 대상. (PST-043, PLC-023)
 *
 * <p>요구사항 근거가 있는 범위만 열어 둔다. 데이터 설계 메모에는 댓글·사용자까지 열어 두자는
 * 논의가 있었지만, 댓글 신고를 넣으면 {@code comment_status} 에 {@code BLINDED} 도 함께
 * 추가해야 한다 — 결정되기 전에 값을 열면 처리 경로 없는 신고가 쌓인다.
 */
public enum ReportTargetType {

    POST,

    /** 장소 신고. 누적되면 장소를 숨기고 붙은 게시글을 재배치한다 (PLC-023, Could Have). */
    PLACE
}
