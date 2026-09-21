package com.snaphere.api.comment.dto;

import java.util.List;

/**
 * 명세: 3. 응답 스키마 &gt; CommentThread.
 *
 * <p>부모 하나와 그 대댓글 전부를 한 덩어리로 준다. 앱이 부모를 그린 뒤 자식을 따로 요청하면
 * 스레드가 하나씩 늘어날 때마다 왕복이 생긴다 (CMU-013).
 */
public record CommentThreadResponse(
        CommentResponse parent,
        List<CommentResponse> replies
) {
}
