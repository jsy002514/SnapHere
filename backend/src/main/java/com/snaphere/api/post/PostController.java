package com.snaphere.api.post;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.TraceIdFilter;
import com.snaphere.api.post.dto.CreatePostRequest;
import com.snaphere.api.post.dto.CreatePostResponse;
import com.snaphere.api.post.dto.PostDetailResponse;
import com.snaphere.api.post.dto.UpdatePostRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-PST-003 — 게시글 생성.
 *
 * <p>기능 명세: 2.3 사진·캡션·태그 &gt; 게시글 등록
 * <p>요구사항: PST-001 ~ PST-004, PST-016 ~ PST-018, PST-021
 *
 * <p>등급 미리보기(API-PST-002)는 {@link PostTierController} 에 있다. 같은 경로 접두어를 쓰지만
 * 미리보기는 아무것도 만들지 않는 조회성 호출이라 분리해 둔다.
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostCreateService postCreateService;
    private final PostEditService postEditService;
    private final CurrentUserProvider currentUserProvider;

    public PostController(PostCreateService postCreateService,
                          PostEditService postEditService,
                          CurrentUserProvider currentUserProvider) {
        this.postCreateService = postCreateService;
        this.postEditService = postEditService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreatePostResponse>> create(
            @Valid @RequestBody CreatePostRequest request,
            HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        CreatePostResponse created = postCreateService.create(user.userId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/posts/" + created.postId()))
                .body(ApiResponse.ok(created, TraceIdFilter.currentTraceId(httpRequest)));
    }

    /**
     * 캡션·태그·사진 순서를 수정한다. (PST-036)
     *
     * <p>장소·좌표·등급은 요청 본문에 없다. 게시 후 변경을 허용하면 위치 신뢰 체계가 무너진다
     * (PST-037).
     */
    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> update(
            @PathVariable String postId,
            @Valid @RequestBody UpdatePostRequest request,
            HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        PostDetailResponse updated = postEditService.update(parsePostId(postId), user.userId(), request);

        return ResponseEntity.ok(ApiResponse.ok(updated,
                TraceIdFilter.currentTraceId(httpRequest)));
    }

    /**
     * 게시글 삭제. 상태만 바꾸고 사진은 30일 후 배치가 지운다. (PST-038)
     *
     * <p>방문 기록과 이미 받은 뱃지는 남는다 — 실제로 갔던 사실은 사라지지 않는다 (PST-039).
     * 본문이 없는 204 응답이라 공통 봉투를 싣지 않는다.
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable String postId, HttpServletRequest httpRequest) {
        CurrentUser user = currentUserProvider.require(httpRequest);
        postEditService.delete(parsePostId(postId), user.userId());
        return ResponseEntity.noContent().build();
    }

    private static long parsePostId(String postId) {
        return ExternalIds.parsePost(postId);
    }
}
