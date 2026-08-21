package com.ssafy.snaphere.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.snaphere.global.error.ErrorCode;

/**
 * 모든 API 의 공통 응답 껍데기.
 *   성공: { "success": true,  "data": {...} }
 *   실패: { "success": false, "error": { "code": "...", "message": "...", "field": null } }
 *
 * 프론트는 message 가 아니라 code 로 분기한다(다국어).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode code, String message, String field) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), message, field));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, String field) {}
}
