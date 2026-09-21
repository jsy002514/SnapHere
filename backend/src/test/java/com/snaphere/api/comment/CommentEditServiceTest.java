package com.snaphere.api.comment;

import com.snaphere.api.comment.dto.UpdateCommentRequest;
import com.snaphere.api.comment.entity.CommentEntity;
import com.snaphere.api.comment.repository.CommentRepository;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 댓글 수정·삭제 — CMU-016, CMU-017, AUTH-013
 *
 * <p>권한 판정과 삭제 멱등성, 그리고 댓글 수를 언제 줄이는지가 이 서비스의 판단이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentEditServiceTest {

    private static final long POST_ID = 7L;
    private static final long COMMENT_ID = 100L;
    private static final UUID AUTHOR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID STRANGER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Mock private CommentRepository comments;
    @Mock private PostRepository posts;
    @Mock private CommentResponseAssembler assembler;

    private CommentEditService service;

    @BeforeEach
    void setUp() {
        service = new CommentEditService(comments, posts, assembler);
    }

    private static CommentEntity comment() {
        CommentEntity comment = CommentEntity.root(POST_ID, AUTHOR, "원래 댓글");
        ReflectionTestUtils.setField(comment, "commentId", COMMENT_ID);
        return comment;
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).errorCode();
    }

    @Test
    @DisplayName("작성자는 본문을 고칠 수 있다")
    void updatesContent() {
        CommentEntity target = comment();
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(target));

        service.update(COMMENT_ID, AUTHOR, new UpdateCommentRequest("  고친 댓글  "));

        assertThat(target.getContent()).isEqualTo("고친 댓글");
    }

    @Test
    @DisplayName("남의 댓글은 고칠 수 없다 (AUTH-013)")
    void rejectsStrangerUpdate() {
        CommentEntity target = comment();
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.update(COMMENT_ID, STRANGER,
                new UpdateCommentRequest("남의 댓글 수정")))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COMMENT_NOT_AUTHOR));

        assertThat(target.getContent()).isEqualTo("원래 댓글");
    }

    @Test
    @DisplayName("삭제된 댓글은 고칠 수 없다 — 수정으로 삭제를 되돌릴 수는 없다")
    void rejectsUpdateOnDeleted() {
        CommentEntity target = comment();
        target.markDeleted();
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.update(COMMENT_ID, AUTHOR,
                new UpdateCommentRequest("되살리기")))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제하면 상태만 바뀌고 본문이 비워진다. 행은 남는다 (CMU-017)")
    void deleteKeepsRow() {
        CommentEntity target = comment();
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(target));

        service.delete(COMMENT_ID, AUTHOR);

        assertThat(target.getStatus()).isEqualTo(CommentStatus.DELETED);
        assertThat(target.getContent()).isNull();
        verify(comments, never()).delete(any());
        verify(posts).addCommentCount(POST_ID, -1);
    }

    @Test
    @DisplayName("두 번 지워도 성공이지만 댓글 수는 한 번만 줄인다")
    void deleteIsIdempotent() {
        CommentEntity target = comment();
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(target));

        service.delete(COMMENT_ID, AUTHOR);
        service.delete(COMMENT_ID, AUTHOR);

        verify(posts, times(1)).addCommentCount(POST_ID, -1);
    }

    @Test
    @DisplayName("남의 댓글은 지울 수 없다 (AUTH-013)")
    void rejectsStrangerDelete() {
        CommentEntity target = comment();
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.delete(COMMENT_ID, STRANGER))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COMMENT_NOT_AUTHOR));

        assertThat(target.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        verify(posts, never()).addCommentCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("없는 댓글을 지우면 COMMENT_NOT_FOUND")
    void rejectsMissingDelete() {
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(COMMENT_ID, AUTHOR))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("본문 검증이 권한 판정보다 먼저다 — 빈 본문은 조회도 하지 않는다")
    void validatesContentFirst() {
        assertThatThrownBy(() -> service.update(COMMENT_ID, AUTHOR, new UpdateCommentRequest("  ")))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COMMENT_LENGTH_INVALID));

        verify(comments, never()).findById(anyLong());
    }
}
