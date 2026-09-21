package com.snaphere.api.comment;

import com.snaphere.api.comment.dto.CreateCommentRequest;
import com.snaphere.api.comment.entity.CommentEntity;
import com.snaphere.api.comment.repository.CommentRepository;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 대댓글 — CMU-014, CMU-015
 *
 * <p>깊이를 1단계로 눌러 주는지, 게시글을 부모에서 가져오는지가 이 기능의 판단이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentReplyServiceTest {

    private static final long POST_ID = 7L;
    private static final long ROOT_ID = 100L;
    private static final long REPLY_ID = 101L;
    private static final UUID WRITER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock private CommentRepository comments;
    @Mock private PostRepository posts;
    @Mock private CommentResponseAssembler assembler;

    private CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService(comments, posts, assembler, new PagingProperties(20, 50));
        when(posts.findByPostIdAndStatus(POST_ID, PostStatus.ACTIVE))
                .thenReturn(Optional.of(PostEntity.create(WRITER, 1L, null, 1, "캡션",
                        TrustTier.HIGH, 37.5, 127.0, null, PhotoSource.ALBUM)));
        when(comments.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static CommentEntity withId(CommentEntity comment, long commentId) {
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        return comment;
    }

    private static CommentEntity root() {
        return withId(CommentEntity.root(POST_ID, WRITER, "부모"), ROOT_ID);
    }

    /** 이미 대댓글인 댓글. parent_id 가 최상위를 가리킨다. */
    private static CommentEntity existingReply() {
        return withId(CommentEntity.reply(POST_ID, WRITER, ROOT_ID, "자식"), REPLY_ID);
    }

    @Test
    @DisplayName("최상위 댓글에 달면 그 댓글이 부모가 된다")
    void repliesToRoot() {
        when(comments.findById(ROOT_ID)).thenReturn(Optional.of(root()));

        service.reply(ROOT_ID, WRITER, new CreateCommentRequest("답글"));

        ArgumentCaptor<CommentEntity> saved = ArgumentCaptor.forClass(CommentEntity.class);
        verify(comments).save(saved.capture());
        assertThat(saved.getValue().getParentId()).isEqualTo(ROOT_ID);
        assertThat(saved.getValue().getPostId()).isEqualTo(POST_ID);
    }

    @Test
    @DisplayName("대댓글에 달아도 부모는 최상위 댓글이다 — 깊이는 1단계다 (CMU-015)")
    void flattensDepth() {
        when(comments.findById(REPLY_ID)).thenReturn(Optional.of(existingReply()));

        service.reply(REPLY_ID, WRITER, new CreateCommentRequest("답글의 답글"));

        ArgumentCaptor<CommentEntity> saved = ArgumentCaptor.forClass(CommentEntity.class);
        verify(comments).save(saved.capture());
        // 요청은 101 번에 달았지만 저장된 부모는 100 번이다.
        assertThat(saved.getValue().getParentId()).isEqualTo(ROOT_ID);
    }

    @Test
    @DisplayName("없는 댓글에 답글을 달면 COMMENT_NOT_FOUND")
    void rejectsMissingParent() {
        when(comments.findById(ROOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(ROOT_ID, WRITER, new CreateCommentRequest("답글")))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.COMMENT_NOT_FOUND));

        verify(comments, never()).save(any());
        verify(posts, never()).addCommentCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("삭제된 댓글에는 답글을 달 수 없다 — 자리표시자는 대화를 이어 갈 자리가 아니다")
    void rejectsDeletedParent() {
        CommentEntity deleted = root();
        deleted.markDeleted();
        when(comments.findById(ROOT_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.reply(ROOT_ID, WRITER, new CreateCommentRequest("답글")))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.COMMENT_NOT_FOUND));

        verify(comments, never()).save(any());
    }

    @Test
    @DisplayName("게시글이 삭제됐으면 스레드가 남아 있어도 답글을 막는다")
    void rejectsWhenPostGone() {
        when(comments.findById(ROOT_ID)).thenReturn(Optional.of(root()));
        when(posts.findByPostIdAndStatus(POST_ID, PostStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(ROOT_ID, WRITER, new CreateCommentRequest("답글")))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("대댓글도 게시글 댓글 수에 센다")
    void countsTowardPostCommentCount() {
        when(comments.findById(ROOT_ID)).thenReturn(Optional.of(root()));

        service.reply(ROOT_ID, WRITER, new CreateCommentRequest("답글"));

        verify(posts).addCommentCount(POST_ID, 1);
    }

    @Test
    @DisplayName("본문 길이 검증이 부모 조회보다 먼저다")
    void validatesContentFirst() {
        assertThatThrownBy(() -> service.reply(ROOT_ID, WRITER, new CreateCommentRequest(" ")))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.COMMENT_LENGTH_INVALID));

        verify(comments, never()).findById(anyLong());
    }
}
