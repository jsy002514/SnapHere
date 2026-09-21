package com.snaphere.api.search;

import com.snaphere.api.common.error.GlobalExceptionHandler;
import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchControllerWebTest {
    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private SearchService service;
    private CurrentUserProvider users;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(SearchService.class);
        users = mock(CurrentUserProvider.class);
        when(users.optional(any())).thenReturn(Optional.empty());
        when(users.require(any())).thenReturn(new CurrentUser(USER));
        when(service.search(anyString(), anyList(), any(), any(), any(), any()))
                .thenReturn(emptyResult("경복궁"));
        when(service.popular(any(), any())).thenReturn(List.of(
                new SearchDtos.PopularKeyword(1, "경복궁", 3, null)));
        when(service.recent(USER)).thenReturn(List.of());

        mvc = MockMvcBuilders.standaloneSetup(new SearchController(service, users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void searchesSelectedTypesInCommonEnvelope() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "경복궁")
                        .param("types", "PLACE,POST").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.query").value("경복궁"));

        verify(service).search(eq("경복궁"), eq(List.of(SearchType.PLACE, SearchType.POST)),
                eq(null), eq(null), eq(5), eq(Optional.empty()));
    }

    @Test
    void invalidTypeAndNumberReturnCommon400() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "경복궁").param("types", "EVENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        mvc.perform(get("/api/v1/search/popular").param("areaCode", "서울"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    void recentSearchEndpointsRequireCurrentUserAndDeleteWith204() throws Exception {
        mvc.perform(get("/api/v1/me/recent-searches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mvc.perform(delete("/api/v1/me/recent-searches").param("keyword", "경복궁"))
                .andExpect(status().isNoContent());

        verify(service).deleteRecent(USER, "경복궁");
    }

    private static SearchDtos.SearchResult emptyResult(String query) {
        return new SearchDtos.SearchResult(query, SearchDtos.SearchSection.skipped(),
                SearchDtos.SearchSection.skipped(), SearchDtos.SearchSection.skipped(),
                SearchDtos.SearchSection.skipped(), null);
    }
}
