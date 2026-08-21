package com.ssafy.snaphere.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 를 읽어 SecurityContext 와 request attribute 에 넣는다.
 * 토큰이 없거나 잘못돼도 여기서 401 을 내지 않는다 — 공개 조회 API 가 많기 때문.
 * 로그인이 꼭 필요한 곳은 @AuthUser(required = true) 가 막는다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String LOGIN_USER_ATTRIBUTE = "LOGIN_USER";
    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER)) {
            LoginUser loginUser = jwtTokenProvider.parseAccessToken(header.substring(BEARER.length()));
            if (loginUser != null) {
                request.setAttribute(LOGIN_USER_ATTRIBUTE, loginUser);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.role()));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(loginUser, null, authorities));
            }
        }
        chain.doFilter(request, response);
    }
}
