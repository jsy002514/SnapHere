package com.snaphere.api.post.tag;

import com.snaphere.api.post.entity.TagEntity;

/**
 * 명세: 3. 응답 스키마 &gt; TagSuggestion.
 *
 * <p>{@code normalizedName} 을 함께 주는 이유는 앱이 중복을 스스로 판정해야 하기 때문이다.
 * 사용자가 "서 울" 이라고 입력한 뒤 "서울" 추천을 누르면 같은 태그가 두 개로 보이는데, 서버는
 * 등록 시점에 하나로 합친다 — 그 판정 기준을 미리 알려 주어 화면에서 먼저 걸러 낸다 (CMU-025).
 *
 * <p>{@code tagId} 는 아직 없는 태그면 null 이다. 추천 단계에서 태그를 미리 만들지 않는다:
 * 채택하지 않은 추천까지 마스터에 쌓이면 인기 태그 집계가 사용되지 않은 태그로 오염된다.
 */
public record TagSuggestionResponse(
        String name,
        String normalizedName,
        String tagId,
        TagSuggestionSource source
) {
    public static TagSuggestionResponse existing(TagEntity tag) {
        return new TagSuggestionResponse(tag.getName(), tag.getNormalizedName(),
                String.valueOf(tag.getTagId()), TagSuggestionSource.EXISTING);
    }

    public static TagSuggestionResponse of(String rawName, TagEntity found,
                                           TagSuggestionSource source) {
        String name = TagEntity.displayName(rawName);
        String normalized = TagEntity.normalize(rawName);
        return new TagSuggestionResponse(
                name,
                normalized,
                found == null ? null : String.valueOf(found.getTagId()),
                source);
    }
}
