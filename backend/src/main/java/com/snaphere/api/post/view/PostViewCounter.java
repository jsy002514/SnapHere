package com.snaphere.api.post.view;

import java.util.Optional;
import java.util.UUID;

/**
 * 조회수 집계. (PST-042)
 *
 * <p>같은 사용자의 24시간 내 재조회는 세지 않는다. 그러지 않으면 자기 게시글을 새로고침해
 * 조회수를 올릴 수 있고, 조회수가 랭킹 점수에 들어가므로 순위까지 밀려 올라간다.
 *
 * <p>비회원도 상세를 볼 수 있으므로(PST-033) 식별자가 없을 수 있다. 그때는 중복 판정을
 * 할 수 없어 세지 않는다 — 과소 집계가 자가 조작보다 낫다.
 */
public interface PostViewCounter {

    /**
     * @return 이번 조회를 새로 셌으면 true. 24시간 내 재조회거나 식별할 수 없으면 false
     */
    boolean countIfFirstToday(long postId, Optional<UUID> viewerId);
}
