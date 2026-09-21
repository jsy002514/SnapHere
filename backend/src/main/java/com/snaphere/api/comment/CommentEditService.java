package com.snaphere.api.comment;

import com.snaphere.api.comment.dto.CommentResponse;
import com.snaphere.api.comment.dto.UpdateCommentRequest;
import com.snaphere.api.comment.entity.CommentEntity;
import com.snaphere.api.comment.repository.CommentRepository;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * API-CMU-007 · API-CMU-008 — 댓글 수정·삭제.
 *
 * <p>기능 명세: 5.3 댓글 &gt; 댓글 삭제
 * <p>요구사항: CMU-016, CMU-017, AUTH-013
 *
 * <p>작성·조회와 갈라 둔다. 이쪽의 판단은 "이 사람이 이 댓글의 주인인가"와 "지울 때 무엇을
 * 남기는가" 두 가지뿐이고, 작성 경로와 섞이면 권한 판정이 어디에 걸려 있는지 흐려진다.
 */
@Service
public class CommentEditService {

    private final CommentRepository comments;
    private final PostRepository posts;
    private final CommentResponseAssembler assembler;

    public CommentEditService(CommentRepository comments,
                              PostRepository posts,
                              CommentResponseAssembler assembler) {
        this.comments = comments;
        this.posts = posts;
        this.assembler = assembler;
    }

    /**
     * 본문을 고친다. (CMU-016, AUTH-013)
     *
     * <p>삭제된 댓글은 고칠 수 없다 — 본문이 없는 자리표시자에 글을 넣으면 삭제를 되돌리는
     * 셈이고, 그건 복구 기능이지 수정이 아니다.
     */
    @Transactional
    public CommentResponse update(long commentId, UUID userId, UpdateCommentRequest request) {
        String content = CommentContent.require(request.content());

        CommentEntity comment = comments.findById(commentId)
                .filter(CommentEntity::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
        requireAuthor(comment, userId);

        comment.changeContent(content);
        return assembler.response(comment, Set.of());
    }

    /**
     * 논리 삭제. 자식이 있으면 자리표시자로 남는다. (CMU-017, AUTH-013)
     *
     * <p>자식이 없어도 행을 지우지 않는다. 목록 조회가 "살아 있는 자식이 없는 삭제된 댓글"을
     * 이미 걸러 내므로 화면에서는 사라지고, 물리 삭제를 넣으면 자식 유무에 따라 삭제 경로가
     * 두 개로 갈린다 — 경로가 둘이면 자식이 동시에 달릴 때 어느 쪽이 이겼는지에 따라 결과가
     * 달라진다.
     *
     * <p>이미 삭제된 댓글을 다시 지워도 성공이다 (멱등). 대신 {@code comment_count} 는 상태가
     * 실제로 바뀐 첫 호출에서만 줄인다 — 연달아 눌리면 댓글 수가 음수로 내려간다.
     */
    @Transactional
    public void delete(long commentId, UUID userId) {
        CommentEntity comment = comments.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
        requireAuthor(comment, userId);

        if (comment.markDeleted()) {
            posts.addCommentCount(comment.getPostId(), -1);
        }
    }

    /** 작성자 본인만 고치고 지운다. (AUTH-013) */
    private void requireAuthor(CommentEntity comment, UUID userId) {
        if (!comment.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.COMMENT_NOT_AUTHOR);
        }
    }
}
