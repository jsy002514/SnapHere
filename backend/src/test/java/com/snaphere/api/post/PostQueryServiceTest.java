package com.snaphere.api.post;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.media.MediaProcessingStateStore;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import com.snaphere.api.post.view.PostViewCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 게시글 상세 조회 — PST-033, PST-038, PST-042
 *
 * <p>가려진 게시글을 누가 볼 수 있는지, 조회수를 언제 올리는지가 이 서비스의 판단이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostQueryServiceTest {

    private static final long POST_ID = 7L;
    private static final UUID AUTHOR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID STRANGER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Mock private PostRepository posts;
    @Mock private PostResponseAssembler assembler;
    @Mock private PostViewCounter viewCounter;
    @Mock private PostImageRepository images;
    @Mock private MediaProcessingStateStore mediaStates;

    private PostQueryService service;

    @BeforeEach
    void setUp() {
        service = new PostQueryService(posts, assembler, viewCounter, images, mediaStates);
        when(images.isPostReady(anyLong())).thenReturn(true);
        // 조립 결과는 이 테스트의 관심이 아니다. Mockito 기본값(null)을 그대로 쓴다.
        when(viewCounter.countIfFirstToday(anyLong(), any())).thenReturn(false);
    }

    private static PostEntity activePost() {
        return PostEntity.create(AUTHOR, 1L, null, 1, "내용", TrustTier.HIGH,
                37.5, 127.0, null, PhotoSource.ALBUM);
    }

    private static PostEntity deletedPost() {
        PostEntity post = activePost();
        post.softDelete();
        return post;
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).errorCode();
    }

    @Test
    @DisplayName("없는 게시글은 POST_NOT_FOUND")
    void 없는_게시글() {
        when(posts.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(POST_ID, Optional.of(STRANGER)))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 게시글은 남에게 POST_NOT_VISIBLE")
    void 삭제된_게시글_타인() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(deletedPost()));

        assertThatThrownBy(() -> service.detail(POST_ID, Optional.of(STRANGER)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
    }

    @Test
    @DisplayName("삭제된 게시글은 비회원에게도 POST_NOT_VISIBLE")
    void 삭제된_게시글_비회원() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(deletedPost()));

        assertThatThrownBy(() -> service.detail(POST_ID, Optional.empty()))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
    }

    @Test
    @DisplayName("삭제된 게시글은 작성자 본인에게는 보인다")
    void 삭제된_게시글_작성자() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(deletedPost()));

        service.detail(POST_ID, Optional.of(AUTHOR));

        verify(assembler).detail(any(), any());
    }

    @Test
    @DisplayName("공개 게시글은 비회원도 볼 수 있다")
    void 비회원_조회() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(activePost()));

        service.detail(POST_ID, Optional.empty());

        verify(assembler).detail(any(), any());
    }

    @Test
    @DisplayName("첫 조회로 판정되면 조회수를 올린다")
    void 조회수_증가() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(activePost()));
        when(viewCounter.countIfFirstToday(POST_ID, Optional.of(STRANGER))).thenReturn(true);

        service.detail(POST_ID, Optional.of(STRANGER));

        verify(posts).increaseViewCount(POST_ID);
    }

    @Test
    @DisplayName("재조회로 판정되면 조회수를 올리지 않는다")
    void 조회수_중복_제거() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(activePost()));
        when(viewCounter.countIfFirstToday(POST_ID, Optional.of(STRANGER))).thenReturn(false);

        service.detail(POST_ID, Optional.of(STRANGER));

        verify(posts, never()).increaseViewCount(anyLong());
    }

    @Test
    @DisplayName("볼 수 없는 게시글은 조회수도 세지 않는다")
    void 차단된_조회는_집계_안함() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(deletedPost()));

        assertThatThrownBy(() -> service.detail(POST_ID, Optional.of(STRANGER)))
                .isInstanceOf(ApiException.class);

        verify(viewCounter, never()).countIfFirstToday(anyLong(), any());
        verify(posts, never()).increaseViewCount(anyLong());
    }
}
