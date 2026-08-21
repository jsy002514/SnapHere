package com.ssafy.snaphere.domain.comment.controller;

import com.ssafy.snaphere.domain.comment.dto.CommentDtos.*;
import com.ssafy.snaphere.domain.comment.service.CommentService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.common.PageResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "댓글")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 목록", description = "최상위 댓글을 페이징하고 대댓글은 replies 에 담아 함께 준다")
    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<PageResponse<Item>> list(@PathVariable Long postId,
                                                @AuthUser(required = false) LoginUser me,
                                                @Valid PageRequestParam pageParam) {
        return ApiResponse.ok(commentService.list(postId, me == null ? null : me.userId(), pageParam));
    }

    @Operation(summary = "댓글 작성", description = "parentCommentId 를 주면 대댓글. 1단계까지만 허용(COMMENT_002)")
    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<Item> create(@PathVariable Long postId, @AuthUser LoginUser me,
                                    @Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(commentService.create(postId, me.userId(), req));
    }

    @Operation(summary = "댓글 수정 (작성자만)")
    @PatchMapping("/comments/{commentId}")
    public ApiResponse<Void> update(@PathVariable Long commentId, @AuthUser LoginUser me,
                                    @Valid @RequestBody UpdateRequest req) {
        commentService.update(commentId, me.userId(), req);
        return ApiResponse.ok();
    }

    @Operation(summary = "댓글 삭제 (작성자 또는 관리자)")
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(@PathVariable Long commentId, @AuthUser LoginUser me) {
        commentService.delete(commentId, me.userId(), me.isAdmin());
        return ApiResponse.ok();
    }

    @Operation(summary = "댓글 좋아요")
    @PostMapping("/comments/{commentId}/likes")
    public ApiResponse<LikeResponse> like(@PathVariable Long commentId, @AuthUser LoginUser me) {
        return ApiResponse.ok(commentService.like(commentId, me.userId()));
    }

    @Operation(summary = "댓글 좋아요 취소")
    @DeleteMapping("/comments/{commentId}/likes")
    public ApiResponse<LikeResponse> unlike(@PathVariable Long commentId, @AuthUser LoginUser me) {
        return ApiResponse.ok(commentService.unlike(commentId, me.userId()));
    }
}
