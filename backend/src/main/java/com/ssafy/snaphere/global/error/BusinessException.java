package com.ssafy.snaphere.global.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;

    public BusinessException(ErrorCode errorCode) { this(errorCode, null); }

    public BusinessException(ErrorCode errorCode, String field) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.field = field;
    }
}
