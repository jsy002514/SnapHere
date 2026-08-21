package com.ssafy.snaphere.domain.bookmark.controller;

import com.ssafy.snaphere.domain.bookmark.service.BookmarkService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "저장 (북마크)")
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "저장 추가", description = "targetType = POST 또는 PLACE")
    @PostMapping
    public ApiResponse<BookmarkService.Result> add(@AuthUser LoginUser me,
                                                   @RequestParam String targetType,
                                                   @RequestParam Long targetId) {
        return ApiResponse.ok(bookmarkService.toggle(me.userId(), targetType, targetId, true));
    }

    @Operation(summary = "저장 해제")
    @DeleteMapping
    public ApiResponse<BookmarkService.Result> remove(@AuthUser LoginUser me,
                                                      @RequestParam String targetType,
                                                      @RequestParam Long targetId) {
        return ApiResponse.ok(bookmarkService.toggle(me.userId(), targetType, targetId, false));
    }

    @Operation(summary = "저장한 게시물 id 목록")
    @GetMapping("/posts")
    public ApiResponse<List<Long>> posts(@AuthUser LoginUser me,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(bookmarkService.listPostIds(me.userId(),
                Math.max(1, page), Math.min(100, Math.max(1, size))));
    }

    @Operation(summary = "저장한 장소 목록")
    @GetMapping("/places")
    public ApiResponse<List<Map<String, Object>>> places(@AuthUser LoginUser me,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(bookmarkService.listPlaces(me.userId(),
                Math.max(1, page), Math.min(100, Math.max(1, size))));
    }
}
