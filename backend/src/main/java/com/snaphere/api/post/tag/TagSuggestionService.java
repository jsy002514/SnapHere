package com.snaphere.api.post.tag;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.place.ContentType;
import com.snaphere.api.place.EventFixedTagReader;
import com.snaphere.api.place.entity.PlaceEntity;
import com.snaphere.api.place.entity.RegionEntity;
import com.snaphere.api.place.entity.SigunguEntity;
import com.snaphere.api.place.entity.SigunguId;
import com.snaphere.api.place.repository.PlaceRepository;
import com.snaphere.api.place.repository.RegionRepository;
import com.snaphere.api.place.repository.SigunguRepository;
import com.snaphere.api.post.entity.TagEntity;
import com.snaphere.api.post.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API-CMU-011 — 태그 추천.
 *
 * <p>기능 명세: 2.3 사진·캡션·태그 &gt; 해시태그 입력 · 3.3 행사 참여 업로드 &gt; 고정 태그 부여
 * <p>요구사항: PLC-021, CMU-026, CMU-027, CMU-028, CMU-029
 *
 * <p>추천 단계에서는 {@code tags} 에 아무것도 쓰지 않는다. 채택되지 않은 추천까지 마스터에 쌓이면
 * 인기 태그 집계가 한 번도 쓰이지 않은 태그로 오염된다 (CMU-031) — 태그 생성은 게시글 등록
 * 시점에만 일어난다.
 *
 * <p>순서가 곧 화면 순서다. 고정(행사) → 장소 이름 → 지역 → 시군구 → 카테고리 → 입력 접두어
 * 순으로 둔다. 앞의 것이 더 확실히 맞는 태그이고, 사용자는 앞의 두세 개만 누른다.
 */
@Service
public class TagSuggestionService {

    /** 게시글 태그 상한과 같다. 더 준다고 열한 번째를 누를 수 있는 것도 아니다 (PST-004). */
    private static final int MAX_SUGGESTIONS = 10;

    private final PlaceRepository places;
    private final RegionRepository regions;
    private final SigunguRepository sigungu;
    private final TagRepository tags;
    private final EventFixedTagReader eventTags;

    public TagSuggestionService(PlaceRepository places,
                                RegionRepository regions,
                                SigunguRepository sigungu,
                                TagRepository tags,
                                EventFixedTagReader eventTags) {
        this.places = places;
        this.regions = regions;
        this.sigungu = sigungu;
        this.tags = tags;
        this.eventTags = eventTags;
    }

    /**
     * 장소·행사·입력 접두어로 태그를 추천한다. (CMU-026, CMU-027, CMU-028)
     *
     * @param query 사용자가 타이핑 중인 접두어. 비었으면 장소 기반 추천만 준다
     */
    @Transactional(readOnly = true)
    public List<TagSuggestionResponse> suggest(long placeId, Long eventId, String query) {
        Map<String, Candidate> candidates = candidates(placeId, eventId);

        Map<String, TagEntity> existing = findExisting(candidates.keySet());
        List<TagSuggestionResponse> result = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates.values()) {
            TagEntity found = existing.get(candidate.normalized());
            TagSuggestionSource source = candidate.source() == TagSuggestionSource.EVENT_FIXED
                    ? TagSuggestionSource.EVENT_FIXED
                    : (found != null ? TagSuggestionSource.EXISTING : TagSuggestionSource.NEW);
            result.add(TagSuggestionResponse.of(candidate.rawName(), found, source));
        }

        appendQueryMatches(query, candidates.keySet(), result);

        return result.size() <= MAX_SUGGESTIONS ? result : result.subList(0, MAX_SUGGESTIONS);
    }

    /**
     * 이 장소·행사에서 서버가 추천했을 태그의 정규화 이름. (CMU-029)
     *
     * <p>게시글 등록이 이 집합으로 {@code post_tags.is_suggested} 를 채운다. 요청에 "추천에서
     * 골랐다"는 표시를 받지 않는 이유는, 사용자가 추천과 똑같이 타이핑한 경우와 눌러서 채택한
     * 경우를 서버가 구분할 수 없고 구분할 필요도 없기 때문이다 — 지표로 알고 싶은 것은
     * "추천이 실제로 쓰였는가"다.
     */
    @Transactional(readOnly = true)
    public Set<String> suggestedNormalizedNames(Long placeId, Long eventId) {
        if (placeId == null) {
            return Set.of();
        }
        return candidates(placeId, eventId).keySet();
    }

    /**
     * 정규화 이름으로 중복을 제거하면서 순서를 지킨다.
     *
     * <p>"경복궁"이 장소 이름이면서 행사 고정 태그일 수 있다. 그때 먼저 넣은 쪽(고정)을 남긴다 —
     * 고정 태그는 사용자가 뗄 수 없다는 성질이 더 중요하다 (EVT-018).
     */
    private Map<String, Candidate> candidates(long placeId, Long eventId) {
        PlaceEntity place = places.findById(placeId)
                .orElseThrow(() -> new ApiException(ErrorCode.PLACE_NOT_FOUND));

        Map<String, Candidate> ordered = new LinkedHashMap<>();

        if (eventId != null) {
            for (String fixed : eventTags.fixedTagNames(eventId)) {
                put(ordered, fixed, TagSuggestionSource.EVENT_FIXED);
            }
        }

        // 장소 이름 태그 (PLC-021)
        put(ordered, place.getTitle(), TagSuggestionSource.NEW);

        // 지역 태그 (CMU-026)
        if (place.getAreaCode() != null) {
            regions.findById(place.getAreaCode())
                    .map(RegionEntity::getNameKo)
                    .ifPresent(name -> put(ordered, name, TagSuggestionSource.NEW));

            if (place.getSigunguCode() != null) {
                sigungu.findById(new SigunguId(place.getAreaCode(), place.getSigunguCode()))
                        .map(SigunguEntity::getNameKo)
                        .ifPresent(name -> put(ordered, name, TagSuggestionSource.NEW));
            }
        }

        // 카테고리 태그 (CMU-027)
        ContentType.of(place.getContentTypeId())
                .ifPresent(type -> put(ordered, type.tagName(), TagSuggestionSource.NEW));

        return ordered;
    }

    private void put(Map<String, Candidate> ordered, String rawName, TagSuggestionSource source) {
        if (rawName == null || rawName.isBlank()) {
            return;
        }
        String normalized = TagEntity.normalize(rawName);
        if (normalized.isEmpty() || normalized.length() > TagEntity.MAX_NAME_LENGTH) {
            return;
        }
        ordered.putIfAbsent(normalized, new Candidate(rawName, normalized, source));
    }

    private Map<String, TagEntity> findExisting(Set<String> normalizedNames) {
        if (normalizedNames.isEmpty()) {
            return Map.of();
        }
        Map<String, TagEntity> found = new LinkedHashMap<>();
        for (TagEntity tag : tags.findByNormalizedNameIn(new LinkedHashSet<>(normalizedNames))) {
            found.put(tag.getNormalizedName(), tag);
        }
        return found;
    }

    /**
     * 타이핑 중인 접두어로 기존 태그를 뒤에 덧붙인다.
     *
     * <p>장소 기반 추천을 밀어내지 않는다. 접두어 검색은 사용자가 이미 무엇을 쓸지 아는 상황이고,
     * 장소 추천은 사용자가 생각하지 못한 태그를 알려 주는 쪽이다.
     */
    private void appendQueryMatches(String query, Set<String> alreadyIn,
                                    List<TagSuggestionResponse> result) {
        if (query == null || query.isBlank()) {
            return;
        }
        String prefix = TagEntity.normalize(query);
        if (prefix.isEmpty()) {
            return;
        }
        for (TagEntity tag : tags.findTop5ByNormalizedNameStartingWithOrderByUsageCountDesc(prefix)) {
            if (!alreadyIn.contains(tag.getNormalizedName())) {
                result.add(TagSuggestionResponse.existing(tag));
            }
        }
    }

    /** @param source 이미 있는 태그인지는 조회 후에 정해진다. EVENT_FIXED 만 여기서 확정된다 */
    private record Candidate(String rawName, String normalized, TagSuggestionSource source) {
    }
}
