package com.snaphere.api.reaction;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.TraceIdFilter;
import com.snaphere.api.reaction.dto.BookmarkResultResponse;
import com.snaphere.api.reaction.dto.LikeResultResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-PST-009 ~ API-PST-012 — 게시글 좋아요·저장.
 *
 * <p>기능 명세: 5.2 반응 &gt; 좋아요 · 저장
 * <p>요구사항: PST-040, CMU-023, CMU-024
 *
 * <p>토글이 아니라 PUT·DELETE 로 상태를 지정한다. 토글은 재시도에 안전하지 않다 —
 * 응답을 못 받고 다시 보내면 눌렀다 뗀 상태가 된다.
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}")
public class PostReactionController {

    private final PostLikeService postLikeService;
    private final PostBookmarkService postBookmarkService;
    private final CurrentUserProvider currentUserProvider;

    public PostReactionController(PostLikeService postLikeService,
                                  PostBookmarkService postBookmarkService,
                                  CurrentUserProvider currentUserProvider) {
        this.postLikeService = postLikeService;
        this.postBookmarkService = postBookmarkService;
        this.currentUserProvider = currentUserProvider;
    }

    @PutMapping("/like")
    public ResponseEntity<ApiResponse<LikeResultResponse>> like(
            @PathVariable String postId, HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        LikeResultResponse result = postLikeService.like(ExternalIds.parsePost(postId), user.userId());

        return ResponseEntity.ok(ApiResponse.ok(result,
                TraceIdFilter.currentTraceId(httpRequest)));
    }

    @DeleteMapping("/like")
    public ResponseEntity<ApiResponse<LikeResultResponse>> unlike(
            @PathVariable String postId, HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        LikeResultResponse result = postLikeService.unlike(ExternalIds.parsePost(postId), user.userId());

        return ResponseEntity.ok(ApiResponse.ok(result,
                TraceIdFilter.currentTraceId(httpRequest)));
    }

    @PutMapping("/bookmark")
    public ResponseEntity<ApiResponse<BookmarkResultResponse>> bookmark(
            @PathVariable String postId, HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        BookmarkResultResponse result = postBookmarkService.bookmark(ExternalIds.parsePost(postId), user.userId());

        return ResponseEntity.ok(ApiResponse.ok(result,
                TraceIdFilter.currentTraceId(httpRequest)));
    }

    @DeleteMapping("/bookmark")
    public ResponseEntity<ApiResponse<BookmarkResultResponse>> removeBookmark(
            @PathVariable String postId, HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        BookmarkResultResponse result = postBookmarkService.removeBookmark(ExternalIds.parsePost(postId), user.userId());

        return ResponseEntity.ok(ApiResponse.ok(result,
                TraceIdFilter.currentTraceId(httpRequest)));
    }
}
