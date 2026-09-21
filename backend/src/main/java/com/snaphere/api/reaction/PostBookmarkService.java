package com.snaphere.api.reaction;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.PostStatus;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.reaction.dto.BookmarkResultResponse;
import com.snaphere.api.reaction.entity.BookmarkEntity;
import com.snaphere.api.reaction.entity.BookmarkId;
import com.snaphere.api.reaction.repository.BookmarkRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API-PST-011 · API-PST-012 — 게시글 저장. (CMU-023)
 *
 * <p>기능 명세: 5.2 반응 &gt; 저장
 *
 * <p>좋아요와 같은 이유로 멱등하다. 저장 수를 비정규화한 컬럼은 없어서 카운터를 옮기지 않는다 —
 * 저장 수는 어느 화면에도 표시되지 않으므로 미리 세어 둘 이유가 없다.
 *
 * <p>{@code bookmarks} 는 게시글과 장소를 함께 담아 {@code target_id} 에 외래키가 없다.
 * 그래서 저장 전에 대상이 실제로 있는지 애플리케이션이 확인한다 (CMU-024).
 */
@Service
public class PostBookmarkService {

    private final BookmarkRepository bookmarks;
    private final PostRepository posts;

    public PostBookmarkService(BookmarkRepository bookmarks, PostRepository posts) {
        this.bookmarks = bookmarks;
        this.posts = posts;
    }

    @Transactional
    public BookmarkResultResponse bookmark(long postId, UUID userId) {
        requireVisiblePost(postId);
        BookmarkId id = new BookmarkId(userId, BookmarkTargetType.POST, postId);
        try {
            BookmarkEntity saved = bookmarks.saveAndFlush(
                    BookmarkEntity.of(userId, BookmarkTargetType.POST, postId));
            return BookmarkResultResponse.saved(
                    BookmarkTargetType.POST, postId, saved.getCreatedAt());
        } catch (DataIntegrityViolationException alreadySaved) {
            // 이미 저장돼 있다. 원래 저장 시각을 그대로 준다 — 재시도가 시각을 밀어내면
            // 저장함 정렬이 흔들린다.
            return BookmarkResultResponse.saved(BookmarkTargetType.POST, postId,
                    bookmarks.findById(id).map(BookmarkEntity::getCreatedAt).orElse(null));
        }
    }

    @Transactional
    public BookmarkResultResponse removeBookmark(long postId, UUID userId) {
        // 해제는 대상 존재를 확인하지 않는다. 게시글이 사라졌어도 내 저장함에서는 빠져야 한다.
        bookmarks.deleteByIdUserIdAndIdTargetTypeAndIdTargetId(
                userId, BookmarkTargetType.POST, postId);
        return BookmarkResultResponse.removed(BookmarkTargetType.POST, postId);
    }

    @Transactional(readOnly = true)
    public Optional<BookmarkEntity> find(long postId, UUID userId) {
        return bookmarks.findById(new BookmarkId(userId, BookmarkTargetType.POST, postId));
    }

    /** 저장 대상 검증. (CMU-024) */
    private void requireVisiblePost(long postId) {
        PostEntity post = posts.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND,
                        Map.of("postId", postId)));
        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new ApiException(ErrorCode.POST_NOT_VISIBLE, Map.of("postId", postId));
        }
    }
}
