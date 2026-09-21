package com.snaphere.api.reaction;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import com.snaphere.api.reaction.dto.LikeResultResponse;
import com.snaphere.api.reaction.repository.LikeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

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
 * 게시글 좋아요 — PST-040
 *
 * <p>멱등성이 이 서비스의 핵심이다. 네트워크가 끊겨 앱이 재시도할 때 좋아요가 두 번 세지면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostLikeServiceTest {

    private static final long POST_ID = 7L;
    private static final UUID AUTHOR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID LIKER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Mock private LikeRepository likes;
    @Mock private PostRepository posts;

    private PostLikeService service;

    @BeforeEach
    void setUp() {
        service = new PostLikeService(likes, posts);
        when(posts.findById(POST_ID)).thenReturn(Optional.of(activePost()));
    }

    private static PostEntity activePost() {
        return PostEntity.create(AUTHOR, 1L, null, 1, "내용", TrustTier.HIGH,
                37.5, 127.0, null, PhotoSource.ALBUM);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).errorCode();
    }

    @Test
    @DisplayName("좋아요를 누르면 isLiked=true 와 늘어난 수를 준다")
    void 좋아요() {
        LikeResultResponse result = service.like(POST_ID, LIKER);

        assertThat(result.isLiked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1);
        assertThat(result.targetType()).isEqualTo("POST");
        assertThat(result.targetId()).isEqualTo("7");
        verify(posts).addLikeCount(POST_ID, 1);
    }

    @Test
    @DisplayName("이미 누른 상태에서 다시 눌러도 성공하고 카운터를 올리지 않는다")
    void 좋아요_멱등() {
        when(likes.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        LikeResultResponse result = service.like(POST_ID, LIKER);

        assertThat(result.isLiked()).isTrue();
        assertThat(result.likeCount()).isZero();
        verify(posts, never()).addLikeCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("해제하면 isLiked=false 와 줄어든 수를 준다")
    void 좋아요_해제() {
        when(likes.deleteByIdUserIdAndIdTargetTypeAndIdTargetId(LIKER, LikeTargetType.POST, POST_ID))
                .thenReturn(1);

        LikeResultResponse result = service.unlike(POST_ID, LIKER);

        assertThat(result.isLiked()).isFalse();
        verify(posts).addLikeCount(POST_ID, -1);
    }

    @Test
    @DisplayName("누르지 않은 상태에서 해제해도 실패시키지 않는다 — 결과가 같다")
    void 해제_멱등() {
        when(likes.deleteByIdUserIdAndIdTargetTypeAndIdTargetId(any(), any(), anyLong()))
                .thenReturn(0);

        LikeResultResponse result = service.unlike(POST_ID, LIKER);

        assertThat(result.isLiked()).isFalse();
        verify(posts, never()).addLikeCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("좋아요 수는 음수가 되지 않는다")
    void 음수_방지() {
        when(likes.deleteByIdUserIdAndIdTargetTypeAndIdTargetId(any(), any(), anyLong()))
                .thenReturn(1);

        assertThat(service.unlike(POST_ID, LIKER).likeCount()).isZero();
    }

    @Test
    @DisplayName("없는 게시글은 POST_NOT_FOUND")
    void 없는_게시글() {
        when(posts.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.like(POST_ID, LIKER))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 게시글에는 누를 수 없다")
    void 삭제된_게시글() {
        PostEntity deleted = activePost();
        deleted.softDelete();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.like(POST_ID, LIKER))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
    }

    @Test
    @DisplayName("자기 게시글에도 누를 수 있다 — 랭킹 점수에서만 뺀다 (PST-041)")
    void 자기_좋아요_허용() {
        LikeResultResponse result = service.like(POST_ID, AUTHOR);

        assertThat(result.isLiked()).isTrue();
        verify(posts).addLikeCount(POST_ID, 1);
    }
}
