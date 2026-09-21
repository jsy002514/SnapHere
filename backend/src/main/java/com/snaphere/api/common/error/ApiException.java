package com.snaphere.api.common.error;

import java.util.Map;

/** 도메인에서 던지는 표준 예외. GlobalExceptionHandler 가 실패 봉투로 바꾼다. */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> messageParams;
    private final Integer retryAfterSec;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, Map.of(), null);
    }

    public ApiException(ErrorCode errorCode, Map<String, Object> messageParams) {
        this(errorCode, messageParams, null);
    }

    public ApiException(ErrorCode errorCode, Map<String, Object> messageParams, Integer retryAfterSec) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.messageParams = messageParams == null ? Map.of() : messageParams;
        this.retryAfterSec = retryAfterSec;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> messageParams() {
        return messageParams;
    }

    public Integer retryAfterSec() {
        return retryAfterSec;
    }
}
