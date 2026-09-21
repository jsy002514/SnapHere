package com.snaphere.api.comment.dto;

import com.snaphere.api.comment.CommentStatus;
import com.snaphere.api.comment.entity.CommentEntity;
import com.snaphere.api.post.dto.UserSummaryResponse;

/**
 * 명세: 3. 응답 스키마 &gt; Comment.
 *
 * <p>{@code content} 는 삭제된 댓글이면 null 이다 — 앱이 그 자리에 '삭제된 댓글'을 그린다
 * (CMU-017). 서버가 '삭제된 댓글'이라는 문구를 넣지 않는 이유는 다국어를 앱이 조립하기
 * 때문이다 (SYS-010).
 *
 * <p>{@code isLiked} 는 비회원 조회면 null 이다. 좋아요 자체는 CMU-018 에서 붙는다.
 */
public record CommentResponse(
        String commentId,
        String postId,
        UserSummaryResponse author,
        String parentId,
        String content,
        CommentStatus status,
        int likeCount,
        Boolean isLiked
) {
    public static CommentResponse of(CommentEntity comment,
                                     UserSummaryResponse author,
                                     Boolean isLiked) {
        return new CommentResponse(
                String.valueOf(comment.getCommentId()),
                String.valueOf(comment.getPostId()),
                author,
                comment.getParentId() == null ? null : String.valueOf(comment.getParentId()),
                comment.getContent(),
                comment.getStatus(),
                comment.getLikeCount(),
                isLiked);
    }
}
