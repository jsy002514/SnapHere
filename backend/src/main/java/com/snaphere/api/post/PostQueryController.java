package com.snaphere.api.post;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.CursorPage;
import com.snaphere.api.common.web.TraceIdFilter;
import com.snaphere.api.post.dto.PostDetailResponse;
import com.snaphere.api.post.dto.PostSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * API-PST-004 · API-PST-005 · API-PST-006 — 게시글 목록·인기·상세 조회.
 *
 * <p>기능 명세: 4.1 지역 커뮤니티 · 5.1 본문
 * <p>요구사항: PST-021, PST-033, PST-034, PST-035, PST-042, SOC-013, PLC-013
 *
 * <p>{@code GET /api/v1/**} 는 SecurityConfig 에서 permitAll 이라 토큰 없이 들어온다.
 * 그래서 {@code require} 가 아니라 {@code optional} 을 쓴다 — {@code require} 를 쓰면
 * 비회원 조회가 401 이 된다 (PST-033).
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostQueryController {

    private final PostQueryService postQueryService;
    private final PostFeedService postFeedService;
    private final CurrentUserProvider currentUserProvider;

    public PostQueryController(PostQueryService postQueryService,
                               PostFeedService postFeedService,
                               CurrentUserProvider currentUserProvider) {
        this.postQueryService = postQueryService;
        this.postFeedService = postFeedService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 지역·장소·태그·기간으로 공개 게시글을 조회한다. (PST-034)
     *
     * <p>필터는 모두 선택이고 조합할 수 있다. {@code period} 를 생략하면 최근 7일이다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorPage<PostSummaryResponse>>> list(
            @RequestParam(required = false) Integer areaCode,
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) PostFeedPeriod period,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpRequest) {

        Optional<UUID> viewerId = currentUserProvider.optional(httpRequest)
                .map(CurrentUser::userId);
        CursorPage<PostSummaryResponse> page =
                postFeedService.list(areaCode, placeId, tag, period, cursor, size, viewerId);

        return ResponseEntity.ok(ApiResponse.ok(page,
                TraceIdFilter.currentTraceId(httpRequest)));
    }

    /**
     * 기간별 인기 게시글. (PST-035)
     *
     * <p>경로가 {@code /posts/popular} 라 {@code /posts/{postId}} 와 겹쳐 보이지만, Spring 은
     * 고정 경로를 경로 변수보다 먼저 맞춘다.
     */
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<CursorPage<PostSummaryResponse>>> popular(
            @RequestParam PostFeedPeriod period,
            @RequestParam(required = false) Integer areaCode,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpRequest) {

        Optional<UUID> viewerId = currentUserProvider.optional(httpRequest)
                .map(CurrentUser::userId);
        CursorPage<PostSummaryResponse> page =
                postFeedService.popular(period, areaCode, cursor, size, viewerId);

        return ResponseEntity.ok(ApiResponse.ok(page,
                TraceIdFilter.currentTraceId(httpRequest)));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> detail(
            @PathVariable String postId,
            HttpServletRequest httpRequest) {

        Optional<UUID> viewerId = currentUserProvider.optional(httpRequest)
                .map(CurrentUser::userId);
        PostDetailResponse detail = postQueryService.detail(
                ExternalIds.parsePost(postId), viewerId);

        return ResponseEntity.ok(ApiResponse.ok(detail,
                TraceIdFilter.currentTraceId(httpRequest)));
    }
}
