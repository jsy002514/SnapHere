package com.ssafy.snaphere.global.security;

/** 컨트롤러 파라미터로 주입되는 인증 사용자 정보. */
public record LoginUser(Long userId, String role) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
