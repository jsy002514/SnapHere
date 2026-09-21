package com.snaphere.api.comment;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.comment.dto.CommentResponse;
import com.snaphere.api.comment.dto.CommentThreadResponse;
import com.snaphere.api.comment.dto.CreateCommentRequest;
import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.CursorPage;
import com.snaphere.api.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * API-CMU-004 · API-CMU-005 — 게시글의 댓글 목록·작성.
 *
 * <p>기능 명세: 5.3 댓글 &gt; 댓글 작성·조회
 * <p>요구사항: CMU-012, CMU-013
 *
 * <p>목록은 비회원도 본다 — {@code GET /api/v1/**} 는 permitAll 이라 토큰 없이 들어오므로
 * {@code require} 가 아니라 {@code optional} 을 쓴다. {@code require} 를 쓰면 비회원 조회가
 * 401 이 된다.
 *
 * <p>댓글 ID 로 접근하는 수정·삭제·대댓글은 경로 접두어가 {@code /comments} 라
 * {@link CommentThreadController} 에 따로 둔다.
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final CurrentUserProvider currentUserProvider;

    public CommentController(CommentService commentService,
                             CurrentUserProvider currentUserProvider) {
        this.commentService = commentService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CursorPage<CommentThreadResponse>>> threads(
            @PathVariable String postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpRequest) {

        Optional<UUID> viewerId = currentUserProvider.optional(httpRequest)
                .map(CurrentUser::userId);
        CursorPage<CommentThreadResponse> page =
                commentService.threads(ExternalIds.parsePost(postId), cursor, size, viewerId);

        return ResponseEntity.ok(ApiResponse.ok(page,
                TraceIdFilter.currentTraceId(httpRequest)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @PathVariable String postId,
            @Valid @RequestBody CreateCommentRequest request,
            HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        CommentResponse created = commentService.create(ExternalIds.parsePost(postId), user.userId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, TraceIdFilter.currentTraceId(httpRequest)));
    }
}
