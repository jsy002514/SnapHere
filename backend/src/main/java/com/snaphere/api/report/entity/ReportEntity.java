package com.snaphere.api.report.entity;

import com.snaphere.api.report.ReportAction;
import com.snaphere.api.report.ReportReason;
import com.snaphere.api.report.ReportStatus;
import com.snaphere.api.report.ReportTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 신고 한 건. (PST-043)
 *
 * <p>{@code (reporter_id, target_type, target_id)} UNIQUE 가 중복 신고 규칙이다 (PST-044).
 * 애플리케이션에서 조회 후 삽입하면 연달아 누를 때 두 건이 들어간다.
 */
@Entity
@Table(name = "reports")
public class ReportEntity {

    /** 상세 설명 최대 길이. (API-PST-013) */
    public static final int MAX_DETAIL_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private ReportReason reason;

    @Column(name = "detail", length = MAX_DETAIL_LENGTH)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20)
    private ReportAction action;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ReportEntity() {
    }

    public static ReportEntity of(UUID reporterId, ReportTargetType targetType, Long targetId,
                                  ReportReason reason, String detail) {
        ReportEntity report = new ReportEntity();
        report.reporterId = reporterId;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.detail = detail;
        report.status = ReportStatus.PENDING;
        report.createdAt = OffsetDateTime.now();
        return report;
    }

    /** 운영자 검토 완료. 상태와 시각을 함께 옮긴다 — DB CHECK 가 짝을 요구한다. (SYS-017) */
    public void review(ReportAction action) {
        this.action = action;
        this.status = ReportStatus.RESOLVED;
        this.reviewedAt = OffsetDateTime.now();
    }

    public void reject() {
        this.action = ReportAction.KEEP;
        this.status = ReportStatus.REJECTED;
        this.reviewedAt = OffsetDateTime.now();
    }

    public Long getReportId() {
        return reportId;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public ReportAction getAction() {
        return action;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
