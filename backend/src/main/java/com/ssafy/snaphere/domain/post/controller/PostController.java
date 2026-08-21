package com.ssafy.snaphere.domain.post.controller;

import com.ssafy.snaphere.domain.media.dto.MediaDtos.UploadUrlRequest;
import com.ssafy.snaphere.domain.media.dto.MediaDtos.UploadUrlResponse;
import com.ssafy.snaphere.domain.post.dto.PostDtos.*;
import com.ssafy.snaphere.domain.post.service.PostService;
import com.ssafy.snaphere.domain.tag.service.TagService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.common.PageResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "게시물")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final TagService tagService;

    @Operation(summary = "① 미디어 업로드 URL 발급",
               description = "발급받은 uploadUrl 로 파일을 직접 PUT 한 뒤 ③ POST /posts 를 호출한다. 서버를 거치지 않는다.")
    @PostMapping("/upload-urls")
    public ApiResponse<UploadUrlResponse> uploadUrls(@AuthUser LoginUser me,
                                                     @Valid @RequestBody UploadUrlRequest req) {
        return ApiResponse.ok(postService.issueUploadUrls(me.userId(), req));
    }

    @Operation(summary = "자동 태그 추천",
               description = "좌표 → 지역, 장소 분류 → 카테고리, 기간+반경이 모두 겹치는 행사 → 이벤트 태그")
    @GetMapping("/tag-suggestions")
    public ApiResponse<TagSuggestionResponse> tagSuggestions(
            @AuthUser LoginUser me,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) LocalDate takenAt) {
        return ApiResponse.ok(new TagSuggestionResponse(tagService.suggest(lat, lng, placeId, takenAt)));
    }

    @Operation(summary = "③ 게시물 등록 + Tier 판정 ★",
               description = "⚠️ tier 를 보내지 마세요. 서버가 좌표·촬영시각·촬영방식으로 판정합니다(위변조 방지).")
    @PostMapping
    public ApiResponse<CreateResponse> create(@AuthUser LoginUser me,
                                              @Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(postService.create(me.userId(), req));
    }

    @Operation(summary = "게시물 목록",
               description = "sort=POPULAR 이면 기간별 인기순, 기본은 최신순. thumbnailRatio 가 포함되어 masonry 배치가 가능하다.")
    @GetMapping
    public ApiResponse<PageResponse<ListItem>> list(
            @RequestParam(required = false) Integer areaCode,
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "LATEST") String sort,
            @RequestParam(required = false) String period,
            @RequestParam(required = false, defaultValue = "false") boolean hasMedia,
            @AuthUser(required = false) LoginUser me,
            @Valid PageRequestParam pageParam) {
        return ApiResponse.ok(postService.list(areaCode, placeId, category, sort, period, hasMedia,
                me == null ? null : me.userId(), pageParam));
    }

    @Operation(summary = "팔로잉 피드")
    @GetMapping("/feed")
    public ApiResponse<PageResponse<ListItem>> feed(@AuthUser LoginUser me,
                                                    @Valid PageRequestParam pageParam) {
        return ApiResponse.ok(postService.feed(me.userId(), pageParam));
    }

    @Operation(summary = "게시물 상세")
    @GetMapping("/{postId}")
    public ApiResponse<Detail> detail(@PathVariable Long postId,
                                      @AuthUser(required = false) LoginUser me) {
        return ApiResponse.ok(postService.detail(postId, me == null ? null : me.userId()));
    }

    @Operation(summary = "게시물 수정 (작성자만)")
    @PatchMapping("/{postId}")
    public ApiResponse<Void> update(@PathVariable Long postId, @AuthUser LoginUser me,
                                    @Valid @RequestBody UpdateRequest req) {
        postService.update(postId, me.userId(), req);
        return ApiResponse.ok();
    }

    @Operation(summary = "게시물 삭제 (작성자 또는 관리자)")
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(@PathVariable Long postId, @AuthUser LoginUser me) {
        postService.delete(postId, me.userId(), me.isAdmin());
        return ApiResponse.ok();
    }

    @Operation(summary = "좋아요")
    @PostMapping("/{postId}/likes")
    public ApiResponse<LikeResponse> like(@PathVariable Long postId, @AuthUser LoginUser me) {
        return ApiResponse.ok(postService.like(postId, me.userId()));
    }

    @Operation(summary = "좋아요 취소")
    @DeleteMapping("/{postId}/likes")
    public ApiResponse<LikeResponse> unlike(@PathVariable Long postId, @AuthUser LoginUser me) {
        return ApiResponse.ok(postService.unlike(postId, me.userId()));
    }
}
