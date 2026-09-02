package com.snaphere.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * 앱은 HTTP 상태가 아니라 이 code 로 분기한다. (SYS-002)
 * 명세: 5. 에러 코드 시트. 도메인 코드는 해당 기능 구현 시 함께 추가한다.
 */
public enum ErrorCode {

    // 공통
    COMMON_400(HttpStatus.BAD_REQUEST, "error.common.badRequest"),
    COMMON_404(HttpStatus.NOT_FOUND, "error.common.notFound"),
    COMMON_409(HttpStatus.CONFLICT, "error.common.conflict"),
    COMMON_422(HttpStatus.UNPROCESSABLE_ENTITY, "error.common.unprocessable"),
    COMMON_429(HttpStatus.TOO_MANY_REQUESTS, "error.common.tooManyRequests"),
    COMMON_500(HttpStatus.INTERNAL_SERVER_ERROR, "error.common.internal"),
    COMMON_503(HttpStatus.SERVICE_UNAVAILABLE, "error.common.unavailable"),

    // 인증·권한
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "error.auth.required"),
    AUTH_INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "error.auth.invalidGoogleToken"),
    AUTH_AUDIENCE_MISMATCH(HttpStatus.UNAUTHORIZED, "error.auth.audienceMismatch"),
    AUTH_INVALID_REFRESH(HttpStatus.UNAUTHORIZED, "error.auth.invalidRefresh"),
    AUTH_REFRESH_EXPIRED(HttpStatus.UNAUTHORIZED, "error.auth.refreshExpired"),
    AUTH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "error.auth.tokenReused"),
    AUTH_TERMS_REQUIRED(HttpStatus.FORBIDDEN, "error.auth.termsRequired"),
    USER_NICKNAME_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "error.user.nicknameInvalid"),
    USER_WITHDRAWN(HttpStatus.CONFLICT, "error.user.withdrawn"),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "error.auth.adminRequired");

    private final HttpStatus status;
    private final String messageKey;

    ErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }

    public HttpStatus status() {
        return status;
    }

    /** 서버는 완성 문장을 만들지 않는다. 앱이 이 키로 다국어를 조립한다. (NTF-009, SYS-010) */
    public String messageKey() {
        return messageKey;
    }
}
