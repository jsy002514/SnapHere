package com.ssafy.snaphere.domain.tag.service;

import com.ssafy.snaphere.domain.place.entity.Place;
import com.ssafy.snaphere.domain.place.repository.PlaceRepository;
import com.ssafy.snaphere.domain.post.dto.PostDtos.TagItem;
import com.ssafy.snaphere.domain.post.dto.PostDtos.TagRequest;
import com.ssafy.snaphere.domain.post.dto.PostDtos.TagSuggestion;
import com.ssafy.snaphere.domain.region.repository.RegionRepository;
import com.ssafy.snaphere.domain.tag.entity.PostTagSource;
import com.ssafy.snaphere.domain.tag.entity.Tag;
import com.ssafy.snaphere.domain.tag.entity.TagType;
import com.ssafy.snaphere.domain.tag.repository.PostTagRepository;
import com.ssafy.snaphere.domain.tag.repository.TagRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 처리 + 자동 추천.
 *
 * 자동 추천의 목적은 사용자가 아무것도 입력하지 않아도 게시물에 지역·분류·행사 정보가 붙게 하는 것이다.
 * 태그가 붙지 않으면 지역별 태그 랭킹과 태그 검색이 전부 빈 화면이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    /** 행사 태그를 추천할 반경. 축제장이 넓어 장소 인증 반경보다 크게 잡는다. */
    private static final int EVENT_SUGGEST_RADIUS_M = 2000;
    private static final int MAX_SUGGESTIONS = 8;
    private static final int MAX_TAG_LENGTH = 50;

    /**
     * TourAPI 분류코드 → 태그명. 전체를 다 넣을 필요는 없고, 자주 쓰이는 대분류만 매핑한다.
     * 매핑에 없으면 분류 태그를 붙이지 않는다(엉뚱한 태그를 붙이는 것보다 낫다).
     */
    private static final Map<String, String> CAT1_TAGS = Map.of(
            "A01", "자연",
            "A02", "인문",
            "A03", "레포츠",
            "A04", "쇼핑",
            "A05", "음식",
            "B02", "숙박",
            "C01", "추천코스");

    private static final Map<Integer, String> CONTENT_TYPE_TAGS = Map.of(
            12, "관광지", 14, "문화시설", 15, "축제", 25, "여행코스",
            28, "레포츠", 32, "숙박", 38, "쇼핑", 39, "맛집");

    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final PlaceRepository placeRepository;
    private final RegionRepository regionRepository;

    // ── 자동 추천 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TagSuggestion> suggest(Double lat, Double lng, Long placeId, LocalDate on) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<TagSuggestion> out = new ArrayList<>();

        Place place = placeId == null ? null : placeRepository.findById(placeId).orElse(null);

        // 1) 지역 — 좌표나 장소에서 시도 이름을 뽑는다
        Integer areaCode = place != null ? place.getAreaCode() : null;
        if (areaCode != null) {
            regionRepository.findById(areaCode).ifPresent(r -> {
                if (seen.add(r.getNameKo())) {
                    out.add(new TagSuggestion(r.getNameKo(), PostTagSource.AUTO_REGION.name(), null, null, null));
                }
            });
        }

        // 2) 분류 — 장소의 cat1 / contentTypeId 에서
        if (place != null) {
            String catTag = CAT1_TAGS.get(place.getCat1());
            if (catTag != null && seen.add(catTag)) {
                out.add(new TagSuggestion(catTag, PostTagSource.AUTO_CATEGORY.name(), null, null, null));
            }
            String typeTag = CONTENT_TYPE_TAGS.get(place.getContentTypeId());
            if (typeTag != null && seen.add(typeTag)) {
                out.add(new TagSuggestion(typeTag, PostTagSource.AUTO_CATEGORY.name(), null, null, null));
            }
            // 장소 이름 자체도 유용한 태그다 (예: "한옥마을")
            if (place.getTitle() != null && place.getTitle().length() <= MAX_TAG_LENGTH
                    && seen.add(place.getTitle())) {
                out.add(new TagSuggestion(place.getTitle(), PostTagSource.AUTO_CATEGORY.name(), null, null, null));
            }
        }

        // 3) 행사 — 기간과 반경을 모두 만족하는 것만. 가장 비싸므로 마지막에 한 번만 조회한다.
        if (lat != null && lng != null) {
            LocalDate day = on == null ? LocalDate.now() : on;
            placeRepository.findOngoingEventsNear(lat, lng, EVENT_SUGGEST_RADIUS_M, day, 3)
                    .forEach(e -> {
                        if (e.getTitle() != null && seen.add(e.getTitle())) {
                            out.add(new TagSuggestion(e.getTitle(), PostTagSource.AUTO_EVENT.name(),
                                    e.getPlaceId(),
                                    e.getEventStartDate() == null ? null : e.getEventStartDate().toLocalDate(),
                                    e.getEventEndDate() == null ? null : e.getEventEndDate().toLocalDate()));
                        }
                    });
        }

        return out.size() > MAX_SUGGESTIONS ? out.subList(0, MAX_SUGGESTIONS) : out;
    }

    // ── 태그 연결 ──────────────────────────────────────────────

    /**
     * 요청 태그를 저장하고 게시물에 연결한다. 없는 태그는 만든다.
     * 태그명은 정규화해서 같은 태그가 여러 개 생기지 않게 한다.
     */
    @Transactional
    public List<TagItem> attach(Long postId, List<TagRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();

        LinkedHashSet<String> names = new LinkedHashSet<>();
        Map<String, String> sourceByName = new java.util.HashMap<>();
        for (TagRequest r : requests) {
            String norm = normalize(r.name());
            if (norm == null) continue;
            if (names.add(norm)) sourceByName.put(norm, normalizeSource(r.source()));
        }
        if (names.isEmpty()) return List.of();

        List<String> nameList = new ArrayList<>(names);
        Map<String, Tag> existing = new java.util.HashMap<>();
        tagRepository.findByNameIn(nameList).forEach(t -> existing.put(t.getName(), t));

        List<Long> tagIds = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        List<TagItem> result = new ArrayList<>();

        for (String name : nameList) {
            Tag tag = existing.get(name);
            if (tag == null) {
                String src = sourceByName.get(name);
                TagType type = switch (src) {
                    case "AUTO_REGION" -> TagType.REGION;
                    case "AUTO_CATEGORY" -> TagType.CATEGORY;
                    case "AUTO_EVENT" -> TagType.EVENT;
                    default -> TagType.FREE;
                };
                tag = tagRepository.save(Tag.of(name, type, null));
            }
            tagIds.add(tag.getId());
            sources.add(sourceByName.get(name));
            result.add(new TagItem(tag.getId(), tag.getName(), sourceByName.get(name)));
        }

        postTagRepository.link(postId, tagIds, sources);
        tagRepository.addUsageCount(tagIds, 1);
        return result;
    }

    @Transactional
    public void detachAll(Long postId) {
        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
        if (!tagIds.isEmpty()) tagRepository.addUsageCount(tagIds, -1);
        postTagRepository.unlinkAll(postId);
    }

    @Transactional(readOnly = true)
    public List<TagItem> findByPostId(Long postId) {
        List<Long> ids = postTagRepository.findTagIdsByPostId(postId);
        if (ids.isEmpty()) return List.of();
        return tagRepository.findAllById(ids).stream()
                .map(t -> new TagItem(t.getId(), t.getName(), null)).toList();
    }

    /**
     * 태그명 정규화.
     * · 앞의 # 제거 — 사용자가 "#야경" 으로 입력해도 "야경" 하나로 모인다
     * · 공백·특수문자 제거 — "야 경" 과 "야경" 이 다른 태그가 되는 것을 막는다
     * · 소문자화 — 영문 태그 "Seoul" 과 "seoul" 통합
     */
    static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        while (s.startsWith("#")) s = s.substring(1).trim();
        s = s.replaceAll("[\\s#,]+", "");
        s = s.toLowerCase();
        if (s.isEmpty()) return null;
        return s.length() > MAX_TAG_LENGTH ? s.substring(0, MAX_TAG_LENGTH) : s;
    }

    private static String normalizeSource(String raw) {
        if (raw == null) return PostTagSource.USER.name();
        try {
            return PostTagSource.valueOf(raw.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            return PostTagSource.USER.name();
        }
    }
}
