package com.snaphere.api.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * 현재 요청의 로그인 사용자를 알려준다. (AUTH-011)
 *
 * <p>도메인 코드가 Spring Security 나 JWT 구조를 몰라도 되도록 여기서 끊는다.
 */
public interface CurrentUserProvider {

    /** 로그인 사용자를 반환한다. 인증 정보가 없으면 {@code AUTH_REQUIRED} 로 막는다. */
    CurrentUser require(HttpServletRequest request);

    Optional<CurrentUser> optional(HttpServletRequest request);
}
