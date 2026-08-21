package com.ssafy.snaphere.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러에서 이렇게 쓴다.
 *   public ApiResponse<X> foo(@AuthUser LoginUser loginUser)                  // 로그인 필수
 *   public ApiResponse<X> bar(@AuthUser(required = false) LoginUser me)       // 비로그인 허용
 *
 * required = false 면 비로그인 요청에 null 이 주입된다.
 * 공개 조회 API 에서 isLiked / isFollowing 같은 개인화 필드를 채울 때 이 형태를 쓴다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUser {
    boolean required() default true;
}
