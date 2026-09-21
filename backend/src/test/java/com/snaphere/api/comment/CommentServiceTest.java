package com.snaphere.api.comment;

import com.snaphere.api.comment.dto.CommentResponse;
import com.snaphere.api.comment.dto.CommentThreadResponse;
import com.snaphere.api.comment.dto.CreateCommentRequest;
import com.snaphere.api.comment.entity.CommentEntity;
import com.snaphere.api.comment.repository.CommentRepository;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.web.CursorPage;
import com.snaphere.api.common.web.PagingProperties;
import com.snaphere.api.post.PostStatus;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 댓글 작성·조회 — CMU-012, CMU-013
 *
 * <p>대상 게시글 판정, 댓글 수 증가, 그리고 자식을 한 번에 모아 붙이는지가 이 서비스의 판단이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceTest {

    private static final long POST_ID = 7L;
    private static final UUID WRITER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock private CommentRepository comments;
    @Mock private PostRepository posts;
    @Mock private CommentResponseAssembler assembler;

    private CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService(comments, posts, assembler, new PagingProperties(20, 50));
        // 자리표시자 쿼리는 기본적으로 비어 있다. 필요한 테스트에서만 따로 채운다.
        when(comments.findDeletedRootsWithActiveReplies(anyLong(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(posts.findByPostIdAndStatus(POST_ID, PostStatus.ACTIVE))
                .thenReturn(Optional.of(activePost()));
        when(comments.save(any())).thenAnswer(call -> call.getArgument(0));
        when(assembler.response(any(), any())).thenReturn(null);
    }

    private static PostEntity activePost() {
        return PostEntity.create(WRITER, 1L, null, 1, "캡션", TrustTier.HIGH,
                37.5, 127.0, null, PhotoSource.ALBUM);
    }

    /** DB 가 채우는 식별자를 테스트에서 대신 넣는다. 부모·자식 묶기가 이 값으로 이뤄진다. */
    private static CommentEntity withId(CommentEntity comment, long commentId, OffsetDateTime at) {
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        ReflectionTestUtils.setField(comment, "createdAt", at);
        return comment;
    }

    private static OffsetDateTime at(int minute) {
        return OffsetDateTime.parse("2026-09-03T12:%02d:00Z".formatted(minute));
    }

    @Test
    @DisplayName("삭제된 게시글에는 댓글을 달 수 없고 아무것도 저장하지 않는다")
    void rejectsMissingPost() {
        when(posts.findByPostIdAndStatus(POST_ID, PostStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(POST_ID, WRITER, new CreateCommentRequest("안녕")))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.POST_NOT_FOUND));

        verify(comments, never()).save(any());
        verify(posts, never()).addCommentCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("길이 검증이 게시글 조회보다 먼저다 — 빈 댓글로 조회를 태우지 않는다")
    void validatesContentFirst() {
        assertThatThrownBy(() -> service.create(POST_ID, WRITER, new CreateCommentRequest("   ")))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.COMMENT_LENGTH_INVALID));

        verify(posts, never()).findByPostIdAndStatus(anyLong(), any());
    }

    @Test
    @DisplayName("최상위 댓글로 저장하고 게시글 댓글 수를 1 올린다")
    void createsRootComment() {
        service.create(POST_ID, WRITER, new CreateCommentRequest("  좋은 정보예요  "));

        verify(comments).save(any(CommentEntity.class));
        verify(posts).addCommentCount(POST_ID, 1);
    }

    @Test
    @DisplayName("댓글이 없으면 빈 페이지다. 자식 조회는 아예 나가지 않는다")
    void emptyPage() {
        when(comments.findActiveRoots(eq(POST_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        CursorPage<CommentThreadResponse> page =
                service.threads(POST_ID, null, null, Optional.empty());

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        verify(comments, never()).findRepliesOf(any());
    }

    @Test
    @DisplayName("요청 크기보다 하나 더 읽어 다음 페이지를 판단하고, 마지막 행으로 커서를 만든다")
    void paginatesWithOneExtraRow() {
        // 저장소는 항상 최신순으로 돌려준다 — 테스트 데이터도 그 순서여야 한다.
        List<CommentEntity> roots = new ArrayList<>();
        for (int i = 20; i >= 0; i--) {
            roots.add(withId(CommentEntity.root(POST_ID, WRITER, "댓글 " + i), 100 + i, at(i)));
        }
        when(comments.findActiveRoots(eq(POST_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(roots);
        when(comments.findRepliesOf(any())).thenReturn(List.of());
        when(assembler.responses(any(), any())).thenAnswer(call -> responsesFor(call.getArgument(0)));

        CursorPage<CommentThreadResponse> page =
                service.threads(POST_ID, null, null, Optional.empty());

        assertThat(page.items()).hasSize(20);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
        // 21번째 행(가장 오래된 100번)은 존재 판단에만 쓰고 버린다. 커서는 20번째 행 기준이어야 한다.
        assertThat(CommentCursor.decode(page.nextCursor()).commentId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("대댓글은 부모별로 묶이고, 조회는 IN 절 한 번뿐이다")
    void groupsRepliesInOneQuery() {
        CommentEntity first = withId(CommentEntity.root(POST_ID, WRITER, "부모1"), 1L, at(1));
        CommentEntity second = withId(CommentEntity.root(POST_ID, WRITER, "부모2"), 2L, at(2));
        CommentEntity replyA = withId(CommentEntity.reply(POST_ID, WRITER, 1L, "자식A"), 11L, at(3));
        CommentEntity replyB = withId(CommentEntity.reply(POST_ID, WRITER, 1L, "자식B"), 12L, at(4));

        when(comments.findActiveRoots(eq(POST_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(second, first));
        when(comments.findRepliesOf(any())).thenReturn(List.of(replyA, replyB));
        when(assembler.responses(any(), any())).thenAnswer(call -> responsesFor(call.getArgument(0)));

        CursorPage<CommentThreadResponse> page =
                service.threads(POST_ID, null, null, Optional.empty());

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).replies()).isEmpty();
        assertThat(page.items().get(1).replies()).hasSize(2);
        verify(comments, times(1)).findRepliesOf(any());
    }

    @Test
    @DisplayName("비회원 조회는 좋아요 상태를 모른다 — 빈 집합이 아니라 null 을 넘긴다")
    void anonymousViewerGetsNullLikedState() {
        CommentEntity root = withId(CommentEntity.root(POST_ID, WRITER, "부모"), 1L, at(1));
        when(comments.findActiveRoots(eq(POST_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(root));
        when(comments.findRepliesOf(any())).thenReturn(List.of());
        when(assembler.responses(any(), any())).thenAnswer(call -> responsesFor(call.getArgument(0)));

        service.threads(POST_ID, null, null, Optional.empty());

        // 부모 목록과 자식 목록을 각각 조립하므로 두 번 불린다. 두 번 다 null 이어야 한다.
        verify(assembler, times(2)).responses(any(), isNull());
    }

    private static List<CommentResponse> responsesFor(Collection<CommentEntity> source) {
        List<CommentResponse> result = new ArrayList<>(source.size());
        for (CommentEntity comment : source) {
            result.add(CommentResponse.of(comment, null, null));
        }
        return result;
    }

    @Test
    @DisplayName("좋아요 상태 조립에 쓰는 집합은 작성 직후에는 비어 있다")
    void createPassesEmptyLikedSet() {
        service.create(POST_ID, WRITER, new CreateCommentRequest("좋아요"));
        verify(assembler).response(any(), eq(Set.of()));
    }

    @Test
    @DisplayName("자리표시자는 활성 댓글과 시각 순서대로 섞인다 — 상태별로 나눠 읽고 합친다 (CMU-017)")
    void mergesTombstonesByTime() {
        CommentEntity newest = withId(CommentEntity.root(POST_ID, WRITER, "새 댓글"), 3L, at(30));
        CommentEntity oldest = withId(CommentEntity.root(POST_ID, WRITER, "옛 댓글"), 1L, at(10));
        CommentEntity tombstone = withId(CommentEntity.root(POST_ID, WRITER, "지워짐"), 2L, at(20));
        tombstone.markDeleted();

        when(comments.findActiveRoots(eq(POST_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(newest, oldest));
        when(comments.findDeletedRootsWithActiveReplies(eq(POST_ID), isNull(), isNull(),
                any(Pageable.class))).thenReturn(List.of(tombstone));
        when(comments.findRepliesOf(any())).thenReturn(List.of());
        when(assembler.responses(any(), any())).thenAnswer(call -> responsesFor(call.getArgument(0)));

        CursorPage<CommentThreadResponse> page =
                service.threads(POST_ID, null, null, Optional.empty());

        assertThat(page.items()).extracting(item -> item.parent().commentId())
                .containsExactly("3", "2", "1");
    }

    @Test
    @DisplayName("합친 결과에도 한 행 더 읽기 규칙이 그대로 적용된다")
    void mergeRespectsPageSize() {
        List<CommentEntity> active = new ArrayList<>();
        for (int i = 20; i >= 0; i--) {
            active.add(withId(CommentEntity.root(POST_ID, WRITER, "댓글 " + i), 200 + i, at(i)));
        }
        CommentEntity tombstone = withId(CommentEntity.root(POST_ID, WRITER, "지워짐"), 999L, at(59));
        tombstone.markDeleted();

        when(comments.findActiveRoots(eq(POST_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(active);
        when(comments.findDeletedRootsWithActiveReplies(eq(POST_ID), isNull(), isNull(),
                any(Pageable.class))).thenReturn(List.of(tombstone));
        when(comments.findRepliesOf(any())).thenReturn(List.of());
        when(assembler.responses(any(), any())).thenAnswer(call -> responsesFor(call.getArgument(0)));

        CursorPage<CommentThreadResponse> page =
                service.threads(POST_ID, null, null, Optional.empty());

        assertThat(page.items()).hasSize(20);
        assertThat(page.hasNext()).isTrue();
        // 자리표시자가 가장 최신이라 첫 줄에 온다.
        assertThat(page.items().get(0).parent().commentId()).isEqualTo("999");
    }
}
