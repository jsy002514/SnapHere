package com.snaphere.api.report.repository;

import com.snaphere.api.report.ReportTargetType;
import com.snaphere.api.report.entity.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 신고 조회. (PST-043, PST-044, PST-045) */
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(
            UUID reporterId, ReportTargetType targetType, Long targetId);

    /**
     * 대상의 신고 건수. 자동 블라인드 판정에 쓴다. (PST-045)
     *
     * <p>검토 완료된 신고도 센다. 운영자가 "문제없음"으로 복구했더라도 그 뒤에 다시 3건이
     * 쌓이면 또 가려야 한다 — 복구는 그 시점 판단이고 누적은 계속된다.
     */
    long countByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);
}
