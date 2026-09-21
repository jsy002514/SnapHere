package com.snaphere.api.post;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 인기 목록 커서 — PST-035, SYS-004 */
class PostRankCursorTest {

    @Test
    @DisplayName("담았다가 꺼내면 같은 순위가 나온다")
    void 왕복() {
        assertThat(PostRankCursor.decode(new PostRankCursor(20).encode()).rankNo()).isEqualTo(20);
    }

    @Test
    @DisplayName("커서가 없으면 null — 1위부터다")
    void 첫_페이지() {
        assertThat(PostRankCursor.decode(null)).isNull();
        assertThat(PostRankCursor.decode("  ")).isNull();
    }

    @Test
    @DisplayName("순위는 1 이상이어야 한다")
    void 순위_범위() {
        assertThatThrownBy(() -> PostRankCursor.decode(new PostRankCursor(0).encode()))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.COMMON_400));
    }

    @Test
    @DisplayName("형식이 깨진 커서는 COMMON_400")
    void 잘못된_커서() {
        for (String broken : new String[]{"!!!", "YWJj", "LTU"}) {
            assertThatThrownBy(() -> PostRankCursor.decode(broken))
                    .satisfies(t -> assertThat(((ApiException) t).errorCode())
                            .isEqualTo(ErrorCode.COMMON_400));
        }
    }
}
