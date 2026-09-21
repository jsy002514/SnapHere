package com.snaphere.api.common.security;

import com.snaphere.api.auth.AuthPrincipal;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JwtAuthenticationFilter 가 SecurityContext 에 넣어 둔 {@link AuthPrincipal} 을 읽는다. (AUTH-011)
 *
 * <p>SecurityConfig 가 {@code POST /api/v1/**} 를 authenticated() 로 두었으므로
 * 여기까지 왔다면 이미 유효한 액세스 토큰이 검증된 상태다. 그래도 방어적으로 한 번 더 확인한다.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser require(HttpServletRequest request) {
        return optional(request).orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));
    }

    @Override
    public Optional<CurrentUser> optional(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(new CurrentUser(principal.userId()));
    }
}
