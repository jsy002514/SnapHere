package com.snaphere.api.post;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.dto.PostDetailResponse;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.view.PostViewCounter;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.media.MediaProcessingStateStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API-PST-006 — 게시글 상세 조회. (PST-033)
 *
 * <p>기능 명세: 5.1 본문
 *
 * <p>비회원도 볼 수 있다. 로그인 여부는 조회 가능성이 아니라 조회수 집계(PST-042)와
 * 반응 상태 필드에만 영향을 준다.
 */
@Service
public class PostQueryService {

    private final PostRepository posts;
    private final PostResponseAssembler assembler;
    private final PostViewCounter viewCounter;
    private final PostImageRepository images;
    private final MediaProcessingStateStore mediaStates;

    public PostQueryService(PostRepository posts,
                            PostResponseAssembler assembler,
                            PostViewCounter viewCounter,
                            PostImageRepository images,
                            MediaProcessingStateStore mediaStates) {
        this.posts = posts;
        this.assembler = assembler;
        this.viewCounter = viewCounter;
        this.images = images;
        this.mediaStates = mediaStates;
    }

    @Transactional
    public PostDetailResponse detail(long postId, Optional<UUID> viewerId) {
        PostEntity post = load(postId, viewerId);

        if (!images.isPostReady(postId)) {
            if (viewerId.filter(post::isOwnedBy).isEmpty())
                throw new ApiException(ErrorCode.POST_NOT_VISIBLE,Map.of("postId",postId));
            ErrorCode code="FAILED".equals(mediaStates.status(postId))
                    ? ErrorCode.POST_MEDIA_FAILED : ErrorCode.POST_MEDIA_PROCESSING;
            throw new ApiException(code,Map.of("postId",postId));
        }

        if (viewCounter.countIfFirstToday(postId, viewerId)) {
            posts.increaseViewCount(postId);
        }
        return assembler.detail(post, viewerId);
    }

    /**
     * 삭제·블라인드 게시글은 작성자에게만 보인다.
     *
     * <p>없는 게시글과 가려진 게시글을 <b>다른 코드</b>로 준다. 앱의 처리가 다르기 때문이다 —
     * 없으면 목록으로 돌아가고(POST_NOT_FOUND), 가려졌으면 접근을 차단한다(POST_NOT_VISIBLE).
     * 둘 다 404 인 것은 존재 여부를 흘리지 않기 위한 명세의 선택이다.
     */
    private PostEntity load(long postId, Optional<UUID> viewerId) {
        PostEntity post = posts.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND,
                        Map.of("postId", postId)));

        if (post.getStatus() != PostStatus.ACTIVE
                && viewerId.filter(post::isOwnedBy).isEmpty()) {
            throw new ApiException(ErrorCode.POST_NOT_VISIBLE, Map.of("postId", postId));
        }
        return post;
    }
}
