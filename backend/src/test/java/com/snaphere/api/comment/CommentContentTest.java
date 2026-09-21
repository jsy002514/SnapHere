package com.snaphere.api.comment;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 댓글 본문 규칙 — CMU-012, CMU-016
 *
 * <p>공백을 지운 뒤에 길이를 세는지가 이 규칙의 전부다.
 */
class CommentContentTest {

    @Test
    @DisplayName("양 끝 공백은 지우고 가운데 줄바꿈은 남긴다")
    void stripsEdgesOnly() {
        assertThat(CommentContent.require("  좋은 정보예요\n감사합니다  "))
                .isEqualTo("좋은 정보예요\n감사합니다");
    }

    @Test
    @DisplayName("공백만 보내면 길이 오류 — 공백 1000자가 통과하는 검증은 검증이 아니다")
    void rejectsBlank() {
        assertThatThrownBy(() -> CommentContent.require("        "))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.COMMENT_LENGTH_INVALID));
    }

    @Test
    @DisplayName("null 도 길이 오류다")
    void rejectsNull() {
        assertThatThrownBy(() -> CommentContent.require(null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("1000자는 통과하고 1001자는 막힌다")
    void boundary() {
        assertThat(CommentContent.require("가".repeat(1000))).hasSize(1000);
        assertThatThrownBy(() -> CommentContent.require("가".repeat(1001)))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.COMMENT_LENGTH_INVALID));
    }
}
