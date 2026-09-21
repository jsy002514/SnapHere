package com.snaphere.api.common.web;

import com.snaphere.api.common.error.ErrorBody;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 모든 응답을 감싸는 공통 봉투. (SYS-001)
 * 명세: 4. 공통 규약 > 응답 > 성공/실패 봉투, 3. 응답 스키마 > ApiEnvelope&lt;T&gt;
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorBody error,
        String traceId,
        OffsetDateTime timestamp
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul"); // SYS-005

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, null, traceId, now());
    }

    public static <T> ApiResponse<T> fail(ErrorBody error, String traceId) {
        return new ApiResponse<>(false, null, error, traceId, now());
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(KST);
    }
}
