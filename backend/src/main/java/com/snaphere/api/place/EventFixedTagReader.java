package com.snaphere.api.place;

import java.util.List;

/**
 * 행사 고정 태그 조회 포트. (CMU-028, EVT-017, EVT-018)
 *
 * <p>행사 참여 업로드에는 지역 태그 1개와 행사 이름 태그 1개가 자동으로 붙고 사용자가 뗄 수
 * 없다 (EVT-018). 그 이름은 {@code events} 테이블에서 오는데 이벤트 도메인이 아직 없어서,
 * 게시글 쪽이 그 테이블을 직접 읽지 않도록 포트로 가른다 — 이벤트 담당이 스키마를 정해도
 * 태그 추천 코드는 이 인터페이스만 보면 된다.
 */
public interface EventFixedTagReader {

    /**
     * @return 표시용 태그 이름. 정규화는 호출자가 한다 — 태그 정규화 규칙은 태그 도메인의 것이다
     */
    List<String> fixedTagNames(long eventId);
}
