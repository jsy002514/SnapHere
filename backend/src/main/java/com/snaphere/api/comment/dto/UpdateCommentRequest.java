package com.snaphere.api.comment.dto;

import com.snaphere.api.comment.entity.CommentEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 명세: 2. 요청 파라미터 &gt; API-CMU-007 &gt; UpdateCommentRequest.
 *
 * <p>고칠 수 있는 것은 본문뿐이다. 부모·게시글은 요청에 없다 — 옮길 수 있게 하면 다른 글의
 * 스레드로 댓글을 이동시킬 수 있고, 그건 수정이 아니라 다른 기능이다.
 */
public record UpdateCommentRequest(
        @NotBlank @Size(max = CommentEntity.MAX_CONTENT_LENGTH) String content
) {
}
