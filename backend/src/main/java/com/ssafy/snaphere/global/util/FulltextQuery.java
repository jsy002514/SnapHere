package com.ssafy.snaphere.global.util;

/**
 * 사용자 검색어 → MySQL ngram FULLTEXT BOOLEAN MODE 질의 변환.
 *
 * 장소 이름 검색(places)과 게시물 본문 검색(posts)이 같은 규칙을 써야 하므로 여기 둔다.
 *
 * ⚠️ 사용자 입력을 그대로 MATCH ... AGAINST 에 넣으면 안 된다.
 *   · `+`, `-`, `>`, `<`, `~`, `*`, `"`, `@`, `(`, `)` 는 BOOLEAN MODE 연산자다.
 *   · 특히 `-서울` 은 "서울을 제외하라" 는 뜻이 되어 의도와 정반대로 동작한다.
 *   · 짝이 안 맞는 `"` 나 `(` 는 SQL 실행 자체를 실패시킨다.
 *
 * ⚠️ ngram_token_size = 2 이므로 1글자 토큰은 색인되지 않는다. 버려야 결과가 엉키지 않는다.
 */
public final class FulltextQuery {

    /** ngram 최소 토큰 길이. MySQL 의 ngram_token_size 기본값과 같아야 한다. */
    private static final int MIN_TOKEN_LENGTH = 2;

    private static final String OPERATORS = "[+\\-><()~*\"@]";

    private FulltextQuery() {}

    /**
     * @return BOOLEAN MODE 질의. 유효한 토큰이 없으면 null — 호출부는 검색을 건너뛴다.
     *         (null 을 그대로 AGAINST 에 넣으면 0건이 아니라 오류가 난다)
     */
    public static String toBooleanMode(String keyword) {
        if (keyword == null) return null;

        String cleaned = keyword.replaceAll(OPERATORS, " ").trim();
        if (cleaned.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (String token : cleaned.split("\\s+")) {
            if (token.length() < MIN_TOKEN_LENGTH) continue;
            // + = 필수 포함(AND), * = 접미 와일드카드
            sb.append('+').append(token).append('*').append(' ');
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }
}
