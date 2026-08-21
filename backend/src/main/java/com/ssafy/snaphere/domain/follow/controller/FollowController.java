package com.ssafy.snaphere.domain.follow.controller;

import com.ssafy.snaphere.domain.follow.service.FollowService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "팔로우")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "팔로우")
    @PostMapping("/users/{userId}/follow")
    public ApiResponse<FollowService.FollowResult> follow(@PathVariable Long userId,
                                                          @AuthUser LoginUser me) {
        return ApiResponse.ok(followService.follow(me.userId(), userId));
    }

    @Operation(summary = "언팔로우")
    @DeleteMapping("/users/{userId}/follow")
    public ApiResponse<FollowService.FollowResult> unfollow(@PathVariable Long userId,
                                                            @AuthUser LoginUser me) {
        return ApiResponse.ok(followService.unfollow(me.userId(), userId));
    }

    @Operation(summary = "팔로워 목록")
    @GetMapping("/users/{userId}/followers")
    public ApiResponse<List<FollowService.UserBrief>> followers(
            @PathVariable Long userId, @AuthUser(required = false) LoginUser me,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(followService.followers(userId, me == null ? null : me.userId(),
                Math.max(1, page), Math.min(100, Math.max(1, size))));
    }

    @Operation(summary = "팔로잉 목록")
    @GetMapping("/users/{userId}/followings")
    public ApiResponse<List<FollowService.UserBrief>> followings(
            @PathVariable Long userId, @AuthUser(required = false) LoginUser me,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(followService.followings(userId, me == null ? null : me.userId(),
                Math.max(1, page), Math.min(100, Math.max(1, size))));
    }

    @Operation(summary = "추천 사용자",
               description = "팔로우 관계가 없으면 인기 사용자로 채운다. 신규 사용자에게 빈 목록을 주지 않기 위함.")
    @GetMapping("/follow-suggestions")
    public ApiResponse<List<FollowService.UserBrief>> suggestions(
            @AuthUser LoginUser me, @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(followService.suggestions(me.userId(), Math.min(50, Math.max(1, limit))));
    }
}
