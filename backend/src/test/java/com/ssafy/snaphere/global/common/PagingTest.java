package com.ssafy.snaphere.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 페이징 규약.
 *
 * ⚠️ tie-breaker 가 이 테스트의 핵심이다. 정렬 기준에 PK 가 없으면
 *    동점 구간(좋아요 수가 같은 게시물 등)에서 페이지 경계가 흔들려
 *    같은 글이 1페이지와 2페이지에 동시에 나오거나 아예 빠진다.
 */
class PagingTest {

    @Test
    @DisplayName("API 는 1부터, Spring Data 는 0부터 — 변환이 일어난다")
    void oneBasedConversion() {
        PageRequestParam param = new PageRequestParam();
        param.setPage(1);
        param.setSize(20);
        assertThat(param.toPageable().getPageNumber()).isZero();

        param.setPage(3);
        assertThat(param.toPageable().getPageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("정렬 기준 뒤에 항상 PK 가 붙는다 (tie-breaker)")
    void tieBreakerAppended() {
        PageRequestParam param = new PageRequestParam();
        param.setPage(1);
        param.setSize(20);

        Sort sort = param.toPageable(Sort.by(Sort.Direction.DESC, "postCount"), "id").getSort();
        List<Sort.Order> orders = sort.toList();

        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("postCount");
        assertThat(orders.get(1).getProperty()).isEqualTo("id");
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("응답의 page 는 다시 1부터로 되돌아온다")
    void responsePageIsOneBased() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);
        PageResponse<String> res = PageResponse.from(page);

        assertThat(res.page()).isEqualTo(1);
        assertThat(res.size()).isEqualTo(2);
        assertThat(res.totalElements()).isEqualTo(5);
        assertThat(res.totalPages()).isEqualTo(3);
        assertThat(res.hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지는 hasNext 가 false")
    void lastPageHasNoNext() {
        var page = new PageImpl<>(List.of("e"), PageRequest.of(2, 2), 5);
        assertThat(PageResponse.from(page).hasNext()).isFalse();
    }

    @Test
    @DisplayName("매퍼로 DTO 변환도 된다")
    void mapsContent() {
        var page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 3), 3);
        PageResponse<String> res = PageResponse.from(page, i -> "n" + i);
        assertThat(res.content()).containsExactly("n1", "n2", "n3");
    }
}
