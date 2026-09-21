package com.snaphere.api.post;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.comment.CommentController;
import com.snaphere.api.comment.CommentService;
import com.snaphere.api.common.error.GlobalExceptionHandler;
import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.post.dto.PostDetailResponse;
import com.snaphere.api.post.dto.PostSummaryResponse;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.share.ShareController;
import com.snaphere.api.post.share.ShareMetadataService;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import com.snaphere.api.reaction.PostBookmarkService;
import com.snaphere.api.reaction.PostLikeService;
import com.snaphere.api.reaction.PostReactionController;
import com.snaphere.api.report.PostReportController;
import com.snaphere.api.report.PostReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PostIdContractTest {
    private final UUID viewer = UUID.randomUUID();
    private final PostQueryService queries = mock(PostQueryService.class);
    private final PostEditService edits = mock(PostEditService.class);
    private final PostLikeService likes = mock(PostLikeService.class);
    private final PostBookmarkService bookmarks = mock(PostBookmarkService.class);
    private final CommentService comments = mock(CommentService.class);
    private final ShareMetadataService shares = mock(ShareMetadataService.class);
    private final PostReportService reports = mock(PostReportService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CurrentUserProvider current = mock(CurrentUserProvider.class);
        when(current.optional(any())).thenReturn(Optional.empty());
        when(current.require(any())).thenReturn(new CurrentUser(viewer));
        mvc = MockMvcBuilders.standaloneSetup(
                new PostQueryController(queries, mock(PostFeedService.class), current),
                new PostController(mock(PostCreateService.class), edits, current),
                new PostReactionController(likes, bookmarks, current),
                new CommentController(comments, current),
                new ShareController(shares),
                new PostReportController(reports, current))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(longs = {2, 10, 36, 42, 1295})
    void summaryIdCanBeUsedForDetail(long id) throws Exception {
        PostEntity post = PostEntity.create(viewer, 1L, null, 1, "본문", TrustTier.LOW,
                null, null, null, PhotoSource.ALBUM);
        ReflectionTestUtils.setField(post, "postId", id);
        PostSummaryResponse summary = PostSummaryResponse.of(post, null, null, List.of(), null, null);
        assertThat(summary.postId()).isEqualTo("pst_" + Long.toString(id, 36));
        when(queries.detail(id, Optional.empty())).thenReturn(
                PostDetailResponse.of(post, summary, List.of(), List.of(), null));

        mvc.perform(get("/api/v1/posts/" + summary.postId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.postId").value(summary.postId()));
        verify(queries).detail(id, Optional.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", "pst_16"})
    void legacyAndExternalIdsReachTheSamePostInRelatedRoutes(String id) throws Exception {
        String path = "/api/v1/posts/" + id;
        mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(delete(path)).andExpect(status().isNoContent());
        mvc.perform(put(path + "/like")).andExpect(status().isOk());
        mvc.perform(delete(path + "/like")).andExpect(status().isOk());
        mvc.perform(put(path + "/bookmark")).andExpect(status().isOk());
        mvc.perform(delete(path + "/bookmark")).andExpect(status().isOk());
        mvc.perform(get(path + "/comments")).andExpect(status().isOk());
        mvc.perform(post(path + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"댓글\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/public/posts/" + id + "/share-metadata")).andExpect(status().isOk());
        mvc.perform(post(path + "/reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isCreated());

        verify(queries).detail(42L, Optional.empty());
        verify(edits).delete(42L, viewer);
        verify(likes).like(42L, viewer);
        verify(likes).unlike(42L, viewer);
        verify(bookmarks).bookmark(42L, viewer);
        verify(bookmarks).removeBookmark(42L, viewer);
        verify(comments).threads(42L, null, null, Optional.empty());
        verify(comments).create(eq(42L), eq(viewer), any());
        verify(shares).metadata(42L);
        verify(reports).report(eq(42L), eq(viewer), any());
    }

    @Test
    void invalidIdIsNotA500AndDoesNotReachThePostService() throws Exception {
        mvc.perform(get("/api/v1/posts/invalid-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
        verifyNoInteractions(queries);
        assertThat(ExternalIds.parsePost("pst_10")).isEqualTo(36L);
    }
}
