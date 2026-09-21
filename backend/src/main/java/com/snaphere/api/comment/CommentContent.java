package com.snaphere.api.comment;

import com.snaphere.api.comment.entity.CommentEntity;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;

import java.util.Map;

/**
 * 댓글 본문 규칙. 1~1000자. (CMU-012, CMU-016)
 *
 * <p>작성과 수정이 같은 규칙을 쓴다. 두 경로에 따로 쓰면 한쪽만 고쳐질 때 수정으로 빈 댓글을
 * 만들 수 있다.
 *
 * <p>양 끝 공백을 지운 <b>뒤</b>에 길이를 센다 — 공백만 1000자를 보내면 통과하는 검증은 검증이
 * 아니다. 가운데 공백과 줄바꿈은 남긴다: 사용자가 쓴 문장이다.
 */
public final class CommentContent {

    private CommentContent() {
    }

    /**
     * @return 저장할 본문
     * @throws ApiException 비었거나 1000자를 넘으면 {@code COMMENT_LENGTH_INVALID}
     */
    public static String require(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.length() < CommentEntity.MIN_CONTENT_LENGTH
                || value.length() > CommentEntity.MAX_CONTENT_LENGTH) {
            throw new ApiException(ErrorCode.COMMENT_LENGTH_INVALID, Map.of(
                    "field", "content",
                    "min", CommentEntity.MIN_CONTENT_LENGTH,
                    "max", CommentEntity.MAX_CONTENT_LENGTH));
        }
        return value;
    }
}
