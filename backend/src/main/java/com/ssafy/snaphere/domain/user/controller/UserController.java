package com.ssafy.snaphere.domain.user.controller;

import com.ssafy.snaphere.domain.user.dto.UserDtos;
import com.ssafy.snaphere.domain.user.service.UserService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "02. 사용자", description = "내 정보 · 프로필 · 계정 삭제")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<UserDtos.MyProfile> me(@AuthUser LoginUser me) {
        return ApiResponse.ok(userService.getMe(me.userId()));
    }

    @Operation(summary = "프로필 수정",
               description = "SNS 링크는 허용 도메인만 저장됩니다. 그 외는 USER_006.")
    @PatchMapping("/me")
    public ApiResponse<UserDtos.MyProfile> updateProfile(
            @AuthUser LoginUser me, @Valid @RequestBody UserDtos.ProfileUpdateRequest req) {
        return ApiResponse.ok(userService.updateProfile(me.userId(), req));
    }

    @Operation(summary = "비밀번호 변경",
               description = "변경 시 다른 기기의 세션을 모두 종료합니다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/me/password")
    public void changePassword(@AuthUser LoginUser me,
                               @Valid @RequestBody UserDtos.PasswordChangeRequest req) {
        userService.changePassword(me.userId(), req);
    }

    @Operation(summary = "이용 약관 동의")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/me/terms")
    public void agreeTerms(@AuthUser LoginUser me) {
        userService.agreeTerms(me.userId());
    }

    @Operation(summary = "알림 설정 변경")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/me/notifications")
    public void notifications(@AuthUser LoginUser me,
                              @RequestBody UserDtos.NotificationSettingRequest req) {
        userService.updateNotificationSettings(me.userId(), req);
    }

    @Operation(summary = "계정 삭제 전 미리보기",
               description = "사라지는 데이터 수를 보여줘 오조작 탈퇴를 줄입니다.")
    @GetMapping("/me/deletion-preview")
    public ApiResponse<UserDtos.DeletionPreview> deletionPreview(@AuthUser LoginUser me) {
        return ApiResponse.ok(userService.deletionPreview(me.userId()));
    }

    @Operation(summary = "계정 삭제",
               description = "개인정보는 즉시 파기되고 계정 레코드는 30일 후 파기됩니다.")
    @DeleteMapping("/me")
    public ApiResponse<UserDtos.DeleteResult> deleteAccount(
            @AuthUser LoginUser me, @Valid @RequestBody UserDtos.AccountDeleteRequest req) {
        return ApiResponse.ok(userService.deleteAccount(me.userId(), req));
    }
}
