package com.ssafy.snaphere.domain.report.service;

import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고. 같은 대상을 같은 사람이 두 번 신고할 수 없다(uk_reports_once).
 * 3회 누적 시 야간 배치가 자동 BLINDED 처리한다(MaintenanceService.autoBlindReported).
 *
 * 즉시 블라인드하지 않는 이유: 경쟁자를 몰아내려는 조직적 신고에 바로 반응하면 악용된다.
 * 대신 신고 수를 실시간으로 세두고 노출 판단은 배치·운영자가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Set<String> TARGET_TYPES = Set.of("POST", "COMMENT", "USER", "PLACE");
    private static final Set<String> REASONS = Set.of(
            "INAPPROPRIATE", "COPYRIGHT", "NOT_THE_PLACE", "SPAM", "DUPLICATE_PLACE", "OTHER");

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Result report(Long reporterId, String targetType, Long targetId,
                         String reason, String detail) {
        String type = requireIn(targetType, TARGET_TYPES, "targetType");
        String rsn = requireIn(reason, REASONS, "reason");

        try {
            jdbcTemplate.update("""
                    INSERT INTO reports (reporter_user_id, target_type, target_id, reason, detail)
                    VALUES (?, ?, ?, ?, ?)
                    """, reporterId, type, targetId, rsn, truncate(detail));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.REPORT_001);
        }

        // 신고 수는 즉시 반영해 운영자가 바로 볼 수 있게 한다.
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reports WHERE target_type = ? AND target_id = ? AND status = 'PENDING'
                """, Integer.class, type, targetId);
        int pending = count == null ? 1 : count;

        if ("POST".equals(type)) {
            jdbcTemplate.update("UPDATE posts SET report_count = ? WHERE post_id = ?", pending, targetId);
        } else if ("COMMENT".equals(type)) {
            jdbcTemplate.update("UPDATE comments SET report_count = ? WHERE comment_id = ?", pending, targetId);
        }

        log.info("[REPORT] reporter={} target={}:{} reason={} 누적={}",
                reporterId, type, targetId, rsn, pending);
        return new Result(type, targetId, pending);
    }

    private static String requireIn(String raw, Set<String> allowed, String field) {
        if (raw == null) throw new BusinessException(ErrorCode.COMMON_400, field);
        String v = raw.trim().toUpperCase();
        if (!allowed.contains(v)) throw new BusinessException(ErrorCode.COMMON_400, field);
        return v;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= 500 ? t : t.substring(0, 500);
    }

    public record Result(String targetType, Long targetId, int pendingReportCount) {}
}
