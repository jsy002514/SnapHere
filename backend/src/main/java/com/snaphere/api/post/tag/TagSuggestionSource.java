package com.snaphere.api.post.tag;

/**
 * 명세: 3. 응답 스키마 &gt; TagSuggestion.source
 *
 * <p>앱이 이 값으로 태그 칩을 다르게 그린다 — 고정 태그는 삭제 버튼을 감추고(EVT-018), 새 태그는
 * "새로 만들어짐" 표시를 붙인다.
 */
public enum TagSuggestionSource {

    /** 이미 있는 태그. tagId 가 있다 */
    EXISTING,

    /** 아직 없는 태그. 채택하면 등록 시점에 만들어진다 */
    NEW,

    /** 행사 고정 태그. 사용자가 뗄 수 없다 (EVT-018) */
    EVENT_FIXED
}
