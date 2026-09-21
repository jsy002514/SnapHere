package com.snaphere.api.post;

import com.snaphere.api.post.entity.TagEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 해시태그 정규화 — CMU-025 */
class TagNormalizeTest {

    @Test
    @DisplayName("앞의 # 을 떼고 공백을 지우고 소문자로 만든다")
    void 정규화_규칙() {
        assertThat(TagEntity.normalize("#Seoul")).isEqualTo("seoul");
        assertThat(TagEntity.normalize("  서 울  ")).isEqualTo("서울");
        assertThat(TagEntity.normalize("K Drama")).isEqualTo("kdrama");
    }

    @Test
    @DisplayName("표기만 다른 태그는 같은 정규화 이름으로 모인다")
    void 같은_태그로_모임() {
        assertThat(TagEntity.normalize("#서울"))
                .isEqualTo(TagEntity.normalize("서 울"))
                .isEqualTo(TagEntity.normalize("서울 "));
    }

    @Test
    @DisplayName("표시용 이름은 # 만 떼고 가운데 공백을 유지한다")
    void 표시_이름_유지() {
        TagEntity tag = TagEntity.of("#드라마 촬영지 ");
        assertThat(tag.getName()).isEqualTo("드라마 촬영지");
        assertThat(tag.getNormalizedName()).isEqualTo("드라마촬영지");
    }
}
