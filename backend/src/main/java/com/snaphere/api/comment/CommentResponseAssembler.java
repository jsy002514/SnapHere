package com.snaphere.api.comment;

import com.snaphere.api.comment.dto.CommentResponse;
import com.snaphere.api.comment.entity.CommentEntity;
import com.snaphere.api.post.dto.UserSummaryResponse;
import com.snaphere.api.user.AuthorSnapshot;
import com.snaphere.api.user.AuthorSnapshotReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 댓글 응답 조립. 작성자 정보를 한 번에 모아 붙인다. (CMU-013, SYS-018)
 *
 * <p>댓글마다 작성자를 조회하면 스레드 20개에 대댓글이 붙는 만큼 쿼리가 늘어난다 — 목록 응답에서
 * N+1 이 가장 잘 생기는 자리다. 부모와 자식의 작성자 ID 를 모아 한 번만 읽는다.
 *
 * <p>탈퇴한 사용자의 댓글은 작성자 정보가 없을 수 있다. 그때 댓글을 빼면 대화에 구멍이 생기므로
 * 작성자만 비운 채 본문을 남긴다 — 읽는 사람에게는 대화의 흐름이 더 중요하다.
 */
@Component
public class CommentResponseAssembler {

    private final AuthorSnapshotReader authors;

    public CommentResponseAssembler(AuthorSnapshotReader authors) {
        this.authors = authors;
    }

    /**
     * @param viewerLikedCommentIds 요청자가 좋아요한 댓글 ID. 비회원이면 null 을 넘긴다 —
     *                              빈 집합과 null 은 다르다: 전자는 "안 눌렀다", 후자는 "모른다"다
     *                              (명세: Comment.isLiked 는 nullable)
     */
    public List<CommentResponse> responses(Collection<CommentEntity> comments,
                                           Set<Long> viewerLikedCommentIds) {
        if (comments.isEmpty()) {
            return List.of();
        }
        Set<UUID> authorIds = new LinkedHashSet<>();
        for (CommentEntity comment : comments) {
            authorIds.add(comment.getUserId());
        }
        Map<UUID, AuthorSnapshot> found = authors.findAllByIds(authorIds);

        List<CommentResponse> result = new ArrayList<>(comments.size());
        for (CommentEntity comment : comments) {
            AuthorSnapshot author = found.get(comment.getUserId());
            Boolean liked = viewerLikedCommentIds == null
                    ? null
                    : viewerLikedCommentIds.contains(comment.getCommentId());
            result.add(CommentResponse.of(comment,
                    author == null ? null : UserSummaryResponse.from(author),
                    liked));
        }
        return result;
    }

    /** 단건 응답. 작성 직후에는 요청자가 방금 만든 댓글이라 좋아요 상태가 항상 false 다. */
    public CommentResponse response(CommentEntity comment, Set<Long> viewerLikedCommentIds) {
        return responses(List.of(comment), viewerLikedCommentIds).get(0);
    }
}
