package com.snaphere.api.report.dto;

import com.snaphere.api.report.entity.ReportEntity;

import java.time.OffsetDateTime;

/**
 * 명세: 3. 응답 스키마 &gt; ReportReceipt
 *
 * <p>접수 사실만 알려 준다. 신고 대상이 실제로 가려졌는지는 담지 않는다 — 신고한 사람에게
 * 처리 결과를 즉시 보여 주면 3건을 모으는 방법을 알려 주는 셈이 된다 (PST-045).
 */
public record ReportReceiptResponse(
        String reportId,
        String status,
        OffsetDateTime createdAt
) {
    public static ReportReceiptResponse from(ReportEntity report) {
        return new ReportReceiptResponse(
                String.valueOf(report.getReportId()),
                report.getStatus().name(),
                report.getCreatedAt());
    }
}
