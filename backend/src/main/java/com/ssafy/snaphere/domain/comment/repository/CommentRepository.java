package com.ssafy.snaphere.domain.comment.repository;

import com.ssafy.snaphere.domain.comment.entity.Comment;
import com.ssafy.snaphere.domain.comment.entity.CommentStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 최상위 댓글 목록. 대댓글은 별도 조회해 한 번에 붙인다(N+1 방지). */
    Page<Comment> findByPostIdAndParentCommentIdIsNullAndStatus(
            Long postId, CommentStatus status, Pageable pageable);

    List<Comment> findByParentCommentIdInAndStatusOrderByIdAsc(
            List<Long> parentIds, CommentStatus status);

    long countByPostIdAndStatus(Long postId, CommentStatus status);
}
