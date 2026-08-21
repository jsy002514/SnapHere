package com.ssafy.snaphere.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.snaphere.domain.post.dto.PostDtos.MediaRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 목록 응답 보조 계산.
 *
 * thumbnailRatio 는 masonry(핀터레스트식) 배치 때문에 목록 단계에서 반드시 필요하다.
 * 없거나 잘못되면 앱이 이미지를 다 받은 뒤에야 높이를 알아 레이아웃이 튄다.
 */
class PostServiceHelperTest {

    private static MediaRequest media(Integer w, Integer h) {
        return new MediaRequest("k", "IMAGE", w, h, null, null, null, 0);
    }

    @Test
    @DisplayName("가로/세로 비율을 소수점 3자리로 계산한다")
    void ratio() {
        assertThat(PostService.ratioOf(media(1000, 1000))).isEqualByComparingTo("1.000");
        assertThat(PostService.ratioOf(media(1920, 1080))).isEqualByComparingTo("1.778");
        assertThat(PostService.ratioOf(media(1080, 1350))).isEqualByComparingTo("0.800");
    }

    @Test
    @DisplayName("치수가 없거나 0이면 null — 0 나눗셈을 만들지 않는다")
    void ratioNullSafe() {
        assertThat(PostService.ratioOf(null)).isNull();
        assertThat(PostService.ratioOf(media(null, 1080))).isNull();
        assertThat(PostService.ratioOf(media(1920, null))).isNull();
        assertThat(PostService.ratioOf(media(1920, 0))).isNull();
        assertThat(PostService.ratioOf(media(0, 1080))).isNull();
        assertThat(PostService.ratioOf(media(-10, 1080))).isNull();
    }

    @Test
    @DisplayName("극단적인 파노라마도 DECIMAL(5,3) 범위 안으로 접는다")
    void ratioClampedToColumnRange() {
        BigDecimal wide = PostService.ratioOf(media(100000, 1));
        assertThat(wide).isEqualByComparingTo("99.999");

        BigDecimal tall = PostService.ratioOf(media(1, 100000));
        assertThat(tall).isEqualByComparingTo("0.001");
    }

    @Test
    @DisplayName("기간 문자열을 집계 시작 시각으로 바꾼다. 알 수 없는 값은 전체 기간(null)")
    void periodFrom() {
        assertThat(PostService.periodFrom("DAY")).isNotNull();
        assertThat(PostService.periodFrom("WEEK")).isNotNull();
        assertThat(PostService.periodFrom("MONTH")).isNotNull();
        assertThat(PostService.periodFrom("ALL")).isNull();
        assertThat(PostService.periodFrom(null)).isNull();
        assertThat(PostService.periodFrom("아무거나")).isNull();
    }
}
