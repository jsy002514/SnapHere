package com.snaphere.api.report.dto;

import com.snaphere.api.report.ReportReason;
import com.snaphere.api.report.entity.ReportEntity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 요청. 명세: 2. 요청 파라미터 &gt; API-PST-013
 *
 * @param detail 상세 설명. 사유가 {@code OTHER} 여도 필수로 두지 않았다 — 명세가 선택이고,
 *               필수로 만들면 신고 자체를 포기하는 사용자가 생긴다
 */
public record CreateReportRequest(

        @NotNull
        ReportReason reason,

        @Size(max = ReportEntity.MAX_DETAIL_LENGTH)
        String detail
) {
}
