package com.ssafy.snaphere.global.security;

import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthUser.class)
                && LoginUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest request, WebDataBinderFactory binder) {
        LoginUser loginUser = (LoginUser) request.getAttribute(
                JwtAuthenticationFilter.LOGIN_USER_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);

        AuthUser annotation = parameter.getParameterAnnotation(AuthUser.class);
        if (loginUser == null && annotation != null && annotation.required()) {
            // 쓰기 동작을 비로그인으로 시도한 경우 → 앱이 로그인 시트를 띄우는 신호
            throw new BusinessException(ErrorCode.AUTH_006);
        }
        return loginUser;
    }
}
