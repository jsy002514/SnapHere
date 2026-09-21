package com.snaphere.api.post.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 조회수 24시간 중복 제거 — PST-042 */
class InMemoryPostViewCounterTest {

    private static final UUID VIEWER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private final InMemoryPostViewCounter counter = new InMemoryPostViewCounter();

    @Test
    @DisplayName("첫 조회는 센다")
    void 첫_조회() {
        assertThat(counter.countIfFirstToday(1L, Optional.of(VIEWER))).isTrue();
    }

    @Test
    @DisplayName("같은 사용자의 재조회는 세지 않는다 — 새로고침으로 조회수를 올릴 수 없다")
    void 재조회_무시() {
        counter.countIfFirstToday(1L, Optional.of(VIEWER));
        assertThat(counter.countIfFirstToday(1L, Optional.of(VIEWER))).isFalse();
        assertThat(counter.countIfFirstToday(1L, Optional.of(VIEWER))).isFalse();
    }

    @Test
    @DisplayName("다른 사용자는 따로 센다")
    void 사용자별_독립() {
        counter.countIfFirstToday(1L, Optional.of(VIEWER));
        assertThat(counter.countIfFirstToday(1L, Optional.of(OTHER))).isTrue();
    }

    @Test
    @DisplayName("다른 게시글은 따로 센다")
    void 게시글별_독립() {
        counter.countIfFirstToday(1L, Optional.of(VIEWER));
        assertThat(counter.countIfFirstToday(2L, Optional.of(VIEWER))).isTrue();
    }

    @Test
    @DisplayName("비회원은 세지 않는다 — 중복 판정 근거가 없어 과소 집계를 택한다")
    void 비회원_제외() {
        assertThat(counter.countIfFirstToday(1L, Optional.empty())).isFalse();
        assertThat(counter.countIfFirstToday(1L, Optional.empty())).isFalse();
        assertThat(counter.trackedCount()).isZero();
    }

    @Test
    @DisplayName("중복 창은 24시간이다")
    void 중복_창_24시간() {
        assertThat(InMemoryPostViewCounter.WINDOW.toHours()).isEqualTo(24);
    }
}
