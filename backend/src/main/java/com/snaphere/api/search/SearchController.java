package com.snaphere.api.search;

import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SearchController {
    private final SearchService searches;
    private final CurrentUserProvider users;

    public SearchController(SearchService searches, CurrentUserProvider users) {
        this.searches = searches;
        this.users = users;
    }

    @GetMapping("/search")
    ApiResponse<SearchDtos.SearchResult> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size,
            HttpServletRequest request) {
        return ok(searches.search(q, parseTypes(types), integer(areaCode, "areaCode"), cursor,
                integer(size, "size"),
                users.optional(request).map(user -> user.userId())), request);
    }

    @GetMapping("/search/popular")
    ApiResponse<List<SearchDtos.PopularKeyword>> popular(
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) String limit,
            HttpServletRequest request) {
        return ok(searches.popular(integer(areaCode, "areaCode"), integer(limit, "limit")), request);
    }

    @GetMapping("/me/recent-searches")
    ApiResponse<List<SearchDtos.RecentSearch>> recent(HttpServletRequest request) {
        return ok(searches.recent(users.require(request).userId()), request);
    }

    @DeleteMapping("/me/recent-searches")
    ResponseEntity<Void> deleteRecent(@RequestParam(required = false) String keyword,
                                      HttpServletRequest request) {
        searches.deleteRecent(users.require(request).userId(), keyword);
        return ResponseEntity.noContent().build();
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.ok(data, TraceIdFilter.currentTraceId(request));
    }

    private static List<SearchType> parseTypes(List<String> raw) {
        if (raw == null) return null;
        List<SearchType> result = new ArrayList<>();
        try {
            for (String group : raw) {
                for (String value : group.split(",")) {
                    if (!value.isBlank()) result.add(SearchType.valueOf(value.strip().toUpperCase(Locale.ROOT)));
                }
            }
            return result;
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.COMMON_400, Map.of("field", "types"));
        }
    }

    private static Integer integer(String raw, String field) {
        if (raw == null) return null;
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException invalid) {
            throw new ApiException(ErrorCode.COMMON_400, Map.of("field", field));
        }
    }
}
