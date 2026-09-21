package com.snaphere.api.comment.dto;

import com.snaphere.api.comment.entity.CommentEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 명세: 2. 요청 파라미터 &gt; API-CMU-005 · API-CMU-006 &gt; CreateCommentRequest.
 *
 * <p>작성과 대댓글이 같은 본문을 쓴다. 부모는 경로에서 온다 — 본문에 부모 ID 를 두면 경로와
 * 본문이 다른 요청을 받게 되고, 어느 쪽을 믿을지 정해야 한다.
 *
 * <p>애노테이션은 형식만 본다. 길이 판정은 {@code CommentContent} 가 공백을 지운 뒤에 다시 하며
 * {@code COMMENT_LENGTH_INVALID} 를 던진다 — 앱이 분기할 코드는 그쪽이다.
 */
public record CreateCommentRequest(
        @NotBlank @Size(max = CommentEntity.MAX_CONTENT_LENGTH) String content
) {
}
