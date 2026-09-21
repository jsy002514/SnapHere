package com.snaphere.api.reaction;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.PostStatus;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.reaction.dto.LikeResultResponse;
import com.snaphere.api.reaction.entity.LikeEntity;
import com.snaphere.api.reaction.entity.LikeId;
import com.snaphere.api.reaction.repository.LikeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * API-PST-009 · API-PST-010 — 게시글 좋아요. (PST-040)
 *
 * <p>기능 명세: 5.2 반응 &gt; 좋아요
 *
 * <p><b>멱등하다.</b> 이미 누른 상태에서 다시 PUT 하면 성공으로 응답하고 카운터를 올리지 않는다.
 * 네트워크가 끊겨 앱이 재시도할 때 좋아요가 두 번 세지면 안 된다. 그래서 명세도 토글이 아니라
 * PUT·DELETE 로 상태를 지정하게 되어 있다.
 *
 * <p>{@code posts.like_count} 는 비정규화한 값이다. 실제로 행이 들어가거나 빠졌을 때만 옮긴다.
 */
@Service
public class PostLikeService {

    private final LikeRepository likes;
    private final PostRepository posts;

    public PostLikeService(LikeRepository likes, PostRepository posts) {
        this.likes = likes;
        this.posts = posts;
    }

    @Transactional
    public LikeResultResponse like(long postId, UUID userId) {
        PostEntity post = loadLikeable(postId);
        try {
            likes.saveAndFlush(LikeEntity.of(userId, LikeTargetType.POST, postId));
            posts.addLikeCount(postId, 1);
            return result(postId, true, post.getLikeCount() + 1);
        } catch (DataIntegrityViolationException alreadyLiked) {
            // 복합 PK 가 중복을 튕겼다. 이미 누른 상태이므로 성공으로 본다 (PST-040).
            return result(postId, true, post.getLikeCount());
        }
    }

    @Transactional
    public LikeResultResponse unlike(long postId, UUID userId) {
        PostEntity post = loadLikeable(postId);
        int removed = likes.deleteByIdUserIdAndIdTargetTypeAndIdTargetId(
                userId, LikeTargetType.POST, postId);
        if (removed == 0) {
            // 누르지 않은 상태에서 해제했다. 결과는 같으므로 실패로 만들지 않는다.
            return result(postId, false, post.getLikeCount());
        }
        posts.addLikeCount(postId, -1);
        return result(postId, false, post.getLikeCount() - 1);
    }

    @Transactional(readOnly = true)
    public boolean isLiked(long postId, UUID userId) {
        return likes.existsById(new LikeId(userId, LikeTargetType.POST, postId));
    }

    /**
     * 좋아요를 누를 수 있는 게시글인지. 삭제·블라인드 게시글에는 누를 수 없다.
     *
     * <p>대상 존재 확인을 애플리케이션이 한다. {@code likes.target_id} 는 게시글과 댓글을 함께
     * 담아 외래키를 걸 수 없다 (CMU-024).
     */
    private PostEntity loadLikeable(long postId) {
        PostEntity post = posts.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND,
                        Map.of("postId", postId)));
        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new ApiException(ErrorCode.POST_NOT_VISIBLE, Map.of("postId", postId));
        }
        return post;
    }

    /**
     * 응답 조립.
     *
     * <p>대상 ID 는 인자로 받은 {@code postId} 를 쓴다. 엔티티에서 다시 꺼내지 않는다 —
     * 호출자가 이미 들고 있는 값이고, 엔티티의 ID 는 {@code Long} 이라 언박싱 위험이 있다.
     *
     * <p>카운터는 방금 UPDATE 한 값을 다시 읽지 않고 계산해서 준다. 같은 트랜잭션 안이라
     * 재조회해도 UPDATE 결과가 보이지 않을 수 있다.
     */
    private LikeResultResponse result(long postId, boolean liked, int likeCount) {
        return LikeResultResponse.of(LikeTargetType.POST, postId, liked, Math.max(0, likeCount));
    }
}
