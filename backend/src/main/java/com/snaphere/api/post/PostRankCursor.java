package com.snaphere.api.post;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 인기 목록 커서. (SYS-004)
 *
 * <p>순위 번호 하나만 담는다. {@code (period, rank_no)} 가 유일하므로 동점 처리를 위한 보조 키가
 * 필요 없다 — 배치가 이미 결정적으로 순위를 매겼다.
 *
 * <p>순위는 10분마다 다시 계산된다. 페이지를 넘기는 사이에 배치가 돌면 같은 게시글이 두 번
 * 보이거나 한 번 건너뛸 수 있다. 스냅샷을 떠 두려면 계산 회차를 커서에 담아야 하는데, 그러면
 * 오래된 회차의 행을 지우지 못해 테이블이 계속 자란다. 인기 목록에서 한두 건이 겹치는 것은
 * 그 비용을 치를 만큼 큰 문제가 아니라고 봤다.
 */
public record PostRankCursor(int rankNo) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(rankNo).getBytes(StandardCharsets.UTF_8));
    }

    /** @return 커서가 없으면 null. 형식이 깨졌으면 {@code COMMON_400} */
    public static PostRankCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int rankNo = Integer.parseInt(raw);
            if (rankNo < 1) {
                throw new IllegalArgumentException(raw);
            }
            return new PostRankCursor(rankNo);
        } catch (IllegalArgumentException malformed) {
            throw new ApiException(ErrorCode.COMMON_400, Map.of("field", "cursor"));
        }
    }
}
