package com.snaphere.api.common.error;

import java.util.List;
import java.util.Map;

/**
 * 실패 봉투의 error 필드. (SYS-002)
 * 명세: 3. 응답 스키마 > ErrorBody
 */
public record ErrorBody(
        String code,
        String messageKey,
        Map<String, Object> messageParams,
        List<Violation> violations,
        Integer retryAfterSec,
        Map<String, Object> details
) {
    /** 필드 단위 검증 오류. (COMMON_400) */
    public record Violation(String field, String reason) {
    }

    public static ErrorBody of(ErrorCode code) {
        return new ErrorBody(code.name(), code.messageKey(), Map.of(), null, null, null);
    }

    public static ErrorBody of(ErrorCode code, Map<String, Object> messageParams) {
        return new ErrorBody(code.name(), code.messageKey(), messageParams, null, null, null);
    }

    public static ErrorBody withViolations(ErrorCode code, List<Violation> violations) {
        return new ErrorBody(code.name(), code.messageKey(), Map.of(), violations, null, null);
    }

    public ErrorBody withRetryAfter(int seconds) {
        return new ErrorBody(code, messageKey, messageParams, violations, seconds, details);
    }
}
