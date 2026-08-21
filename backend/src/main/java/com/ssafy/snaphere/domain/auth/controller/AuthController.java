package com.ssafy.snaphere.domain.auth.controller;

import com.ssafy.snaphere.domain.auth.dto.AuthDtos;
import com.ssafy.snaphere.domain.auth.service.AuthService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "01. 인증", description = "회원가입 · 로그인 · 토큰 재발급")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "아이디 중복 확인",
               description = "가입 시점에 서버가 한 번 더 검증합니다(중복확인 후 선점될 수 있음).")
    @GetMapping("/check-login-id")
    public ApiResponse<AuthDtos.LoginIdAvailability> checkLoginId(
            @RequestParam @NotBlank String loginId) {
        return ApiResponse.ok(authService.checkLoginId(loginId));
    }

    @Operation(summary = "회원가입",
               description = "이메일은 선택이지만, 없으면 비밀번호를 잊었을 때 계정을 복구할 방법이 없습니다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public ApiResponse<AuthDtos.TokenResponse> signup(@Valid @RequestBody AuthDtos.SignupRequest req) {
        return ApiResponse.ok(authService.signup(req));
    }

    @Operation(summary = "아이디·비밀번호 로그인",
               description = "아이디 오류와 비밀번호 오류를 구분해 응답하지 않습니다(계정 존재 여부 노출 방지).")
    @PostMapping("/login")
    public ApiResponse<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @Operation(summary = "구글 로그인", description = "S3 단계에서 검증 로직 구현 예정(현재 AUTH_004 반환)")
    @PostMapping("/google")
    public ApiResponse<AuthDtos.TokenResponse> google(
            @Valid @RequestBody AuthDtos.GoogleLoginRequest req) {
        return ApiResponse.ok(authService.googleLogin(req));
    }

    @Operation(summary = "토큰 재발급",
               description = "리프레시 토큰은 1회용입니다. 재사용이 감지되면 해당 사용자의 전체 토큰을 무효화합니다.")
    @PostMapping("/refresh")
    public ApiResponse<AuthDtos.TokenResponse> refresh(
            @Valid @RequestBody AuthDtos.RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req.refreshToken()));
    }

    @Operation(summary = "로그아웃")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout")
    public void logout(@AuthUser LoginUser me, @Valid @RequestBody AuthDtos.LogoutRequest req) {
        authService.logout(req.refreshToken());
    }

    @Operation(summary = "전체 기기 로그아웃")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout-all")
    public void logoutAll(@AuthUser LoginUser me) {
        authService.logoutAll(me.userId());
    }
}
