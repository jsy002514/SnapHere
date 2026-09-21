package com.snaphere.api.post.tag;

import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.CursorPage;
import com.snaphere.api.common.web.TraceIdFilter;
import com.snaphere.api.post.dto.PostSummaryResponse;
import com.snaphere.api.post.dto.TagSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * API-CMU-011 · API-CMU-012 · API-CMU-013 — 태그 추천·인기 태그·태그 게시글.
 *
 * <p>기능 명세: 1.2 검색 &gt; 태그 검색 · 2.3 사진·캡션·태그 &gt; 해시태그 입력
 * <p>요구사항: PLC-021, CMU-026~031, SCH-007
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    /** 명세의 캐시 10m. 인기 태그는 몇 분 늦어도 되는 값이고 매 요청 집계는 비싸다. */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final TagQueryService tagQueryService;
    private final TagSuggestionService tagSuggestionService;
    private final CurrentUserProvider currentUserProvider;

    public TagController(TagQueryService tagQueryService,
                         TagSuggestionService tagSuggestionService,
                         CurrentUserProvider currentUserProvider) {
        this.tagQueryService = tagQueryService;
        this.tagSuggestionService = tagSuggestionService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 업로드 화면의 태그 추천. (CMU-026, CMU-027, CMU-028)
     *
     * <p>캐시를 두지 않는다. 같은 장소라도 진행 중인 행사가 바뀌면 결과가 달라지고, 타이핑
     * 접두어가 매번 다르다.
     *
     * <p>명세가 이 엔드포인트에만 Bearer 를 요구한다(API-CMU-011). SecurityConfig 는
     * {@code GET /api/v1/**} 를 permitAll 로 두므로 컨트롤러에서 {@code require} 로 막는다 —
     * 인증 담당의 공용 설정을 이 기능 하나 때문에 고치면 다른 조회까지 영향이 간다. 업로드
     * 화면에서만 쓰는 조회라 비회원에게 열어 둘 이유도 없다.
     */
    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<TagSuggestionResponse>>> suggestions(
            @RequestParam long placeId,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) String query,
            HttpServletRequest httpRequest) {

        currentUserProvider.require(httpRequest);
        List<TagSuggestionResponse> suggestions =
                tagSuggestionService.suggest(placeId, eventId, query);

        return ResponseEntity.ok(ApiResponse.ok(suggestions,
                TraceIdFilter.currentTraceId(httpRequest)));
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<TagSummaryResponse>>> popular(
            @RequestParam(required = false) Integer areaCode,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest httpRequest) {

        List<TagSummaryResponse> tags = tagQueryService.popular(areaCode, limit);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .body(ApiResponse.ok(tags, TraceIdFilter.currentTraceId(httpRequest)));
    }

    /**
     * 태그가 붙은 공개 게시글. (CMU-030)
     *
     * <p>인기 태그와 달리 캐시를 두지 않는다. 태그를 눌러 들어온 목록은 방금 올린 내 글이 보여야
     * 하는 자리다.
     */
    @GetMapping("/{tagId}/posts")
    public ResponseEntity<ApiResponse<CursorPage<PostSummaryResponse>>> posts(
            @PathVariable long tagId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpRequest) {

        CursorPage<PostSummaryResponse> page = tagQueryService.postsByTag(tagId, cursor, size);

        return ResponseEntity.ok(ApiResponse.ok(page,
                TraceIdFilter.currentTraceId(httpRequest)));
    }
}
