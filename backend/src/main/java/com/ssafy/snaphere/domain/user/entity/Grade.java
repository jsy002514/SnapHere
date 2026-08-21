package com.ssafy.snaphere.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인기 지수 구간으로 산출되는 등급.
 * ⚠️ 구간 수치는 아직 팀 확정 전이다. 임시값이며 운영 데이터로 조정할 것.
 *    구간을 좁게 잡으면 초반에 등급이 급하게 올라 의미가 희석된다.
 */
@Getter
@RequiredArgsConstructor
public enum Grade {
    SEED(0), SPROUT(300), TREE(1500), FOREST(5000), LEGEND(20000);

    private final int minScore;

    public static Grade of(int score) {
        Grade result = SEED;
        for (Grade g : values()) {
            if (score >= g.minScore) result = g;
        }
        return result;
    }

    public Integer nextScore() {
        Grade[] all = values();
        for (int i = 0; i < all.length - 1; i++) {
            if (all[i] == this) return all[i + 1].minScore;
        }
        return null;   // LEGEND
    }
}
