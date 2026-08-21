package com.ssafy.snaphere.domain.search.controller;

import com.ssafy.snaphere.domain.search.service.SearchService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "검색")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "통합 검색",
               description = "장소·게시물·태그를 우리 DB(ngram FULLTEXT)에서 찾는다. TourAPI 를 호출하지 않는다.")
    @GetMapping
    public ApiResponse<SearchService.SearchResponse> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer areaCode,
            @RequestParam(defaultValue = "20") int limit,
            @AuthUser(required = false) LoginUser me) {
        return ApiResponse.ok(searchService.search(keyword, areaCode,
                me == null ? null : me.userId(), Math.min(50, Math.max(1, limit))));
    }

    @Operation(summary = "인기 검색어", description = "최근 7일 집계. 로그가 없으면 빈 목록이다.")
    @GetMapping("/popular")
    public ApiResponse<List<Map<String, Object>>> popular(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(searchService.popularKeywords(Math.min(30, Math.max(1, limit))));
    }
}
