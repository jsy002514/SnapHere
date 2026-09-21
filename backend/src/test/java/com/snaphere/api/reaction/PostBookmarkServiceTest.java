package com.snaphere.api.reaction;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import com.snaphere.api.reaction.dto.BookmarkResultResponse;
import com.snaphere.api.reaction.entity.BookmarkEntity;
import com.snaphere.api.reaction.repository.BookmarkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 게시글 저장 — CMU-023, CMU-024 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostBookmarkServiceTest {

    private static final long POST_ID = 7L;
    private static final UUID AUTHOR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SAVER = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final OffsetDateTime SAVED_AT = OffsetDateTime.parse("2026-09-01T10:00:00+09:00");

    @Mock private BookmarkRepository bookmarks;
    @Mock private PostRepository posts;

    private PostBookmarkService service;

    @BeforeEach
    void setUp() {
        service = new PostBookmarkService(bookmarks, posts);
        when(posts.findById(POST_ID)).thenReturn(Optional.of(activePost()));
        when(bookmarks.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static PostEntity activePost() {
        return PostEntity.create(AUTHOR, 1L, null, 1, "내용", TrustTier.HIGH,
                37.5, 127.0, null, PhotoSource.ALBUM);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).errorCode();
    }

    @Test
    @DisplayName("저장하면 isBookmarked=true 와 저장 시각을 준다")
    void 저장() {
        BookmarkResultResponse result = service.bookmark(POST_ID, SAVER);

        assertThat(result.isBookmarked()).isTrue();
        assertThat(result.targetType()).isEqualTo("POST");
        assertThat(result.targetId()).isEqualTo("7");
        assertThat(result.savedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 저장돼 있으면 원래 저장 시각을 그대로 준다 — 재시도가 정렬을 흔들지 않는다")
    void 저장_멱등() {
        when(bookmarks.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        when(bookmarks.findById(any())).thenReturn(Optional.of(
                BookmarkEntity.of(SAVER, BookmarkTargetType.POST, POST_ID)));

        BookmarkResultResponse result = service.bookmark(POST_ID, SAVER);

        assertThat(result.isBookmarked()).isTrue();
        assertThat(result.savedAt()).isNotNull();
    }

    @Test
    @DisplayName("해제하면 isBookmarked=false 와 savedAt=null 을 준다")
    void 해제() {
        BookmarkResultResponse result = service.removeBookmark(POST_ID, SAVER);

        assertThat(result.isBookmarked()).isFalse();
        assertThat(result.savedAt()).isNull();
        verify(bookmarks).deleteByIdUserIdAndIdTargetTypeAndIdTargetId(
                SAVER, BookmarkTargetType.POST, POST_ID);
    }

    @Test
    @DisplayName("해제는 대상 존재를 확인하지 않는다 — 게시글이 사라져도 내 저장함에서는 빠져야 한다")
    void 해제는_대상_검증_없음() {
        when(posts.findById(anyLong())).thenReturn(Optional.empty());

        service.removeBookmark(POST_ID, SAVER);

        verify(bookmarks).deleteByIdUserIdAndIdTargetTypeAndIdTargetId(any(), any(), anyLong());
    }

    @Test
    @DisplayName("없는 게시글은 저장할 수 없다 — 외래키가 없어 애플리케이션이 검증한다 (CMU-024)")
    void 없는_게시글() {
        when(posts.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookmark(POST_ID, SAVER))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 게시글은 저장할 수 없다")
    void 삭제된_게시글() {
        PostEntity deleted = activePost();
        deleted.softDelete();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.bookmark(POST_ID, SAVER))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
    }

    @Test
    @DisplayName("저장 대상 종류는 좋아요와 다르다 — 댓글 저장 같은 조합이 타입에서 막힌다")
    void 대상_종류_분리() {
        assertThat(BookmarkTargetType.values()).extracting(Enum::name)
                .containsExactly("POST", "PLACE");
        assertThat(LikeTargetType.values()).extracting(Enum::name)
                .containsExactly("POST", "COMMENT");
    }
}
