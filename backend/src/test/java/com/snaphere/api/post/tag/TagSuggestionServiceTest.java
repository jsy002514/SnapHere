package com.snaphere.api.post.tag;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 태그 추천 — PLC-021, CMU-026, CMU-027, CMU-028, CMU-029
 *
 * <p>무엇을 어떤 순서로 추천하는지, 추천 단계에서 태그를 만들지 않는지가 이 서비스의 판단이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagSuggestionServiceTest {

    private static final long PLACE_ID = 5L;
    private static final long EVENT_ID = 9L;
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock private PlaceRepository places;
    @Mock private RegionRepository regions;
    @Mock private SigunguRepository sigungu;
    @Mock private TagRepository tags;
    @Mock private EventFixedTagReader eventTags;

    private TagSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new TagSuggestionService(places, regions, sigungu, tags, eventTags);

        // 지역·시군구 목의 스텁을 먼저 끝낸다. when(...) 의 인수 자리에서 목을 만들면 앞의
        // 스텁이 닫히기 전에 새 스텁이 시작돼 UnfinishedStubbingException 이 난다.
        RegionEntity seoul = region("서울");
        SigunguEntity jongno = sigungu("종로구");

        when(places.findById(PLACE_ID)).thenReturn(Optional.of(place(12)));
        when(regions.findById(1)).thenReturn(Optional.of(seoul));
        when(sigungu.findById(any(SigunguId.class))).thenReturn(Optional.of(jongno));
        when(tags.findByNormalizedNameIn(any())).thenReturn(List.of());
        when(eventTags.fixedTagNames(EVENT_ID)).thenReturn(List.of());
    }

    private static PlaceEntity place(Integer contentTypeId) {
        PlaceEntity place = PlaceEntity.userPlace("경복궁", "서울 종로구", 37.579, 126.977,
                1, 11, OWNER);
        ReflectionTestUtils.setField(place, "placeId", PLACE_ID);
        ReflectionTestUtils.setField(place, "contentTypeId", contentTypeId);
        return place;
    }

    /**
     * 엔티티 생성자가 protected 라 목으로 만든다. 이 테스트가 보는 것은 이름 한 개뿐이고,
     * 지역 데이터는 마이그레이션 시드(V4)에서 온다.
     */
    private static RegionEntity region(String nameKo) {
        RegionEntity region = org.mockito.Mockito.mock(RegionEntity.class);
        when(region.getNameKo()).thenReturn(nameKo);
        return region;
    }

    private static SigunguEntity sigungu(String nameKo) {
        SigunguEntity entity = org.mockito.Mockito.mock(SigunguEntity.class);
        when(entity.getNameKo()).thenReturn(nameKo);
        return entity;
    }

    private static TagEntity tag(long tagId, String name) {
        TagEntity tag = TagEntity.of(name);
        ReflectionTestUtils.setField(tag, "tagId", tagId);
        return tag;
    }

    @Test
    @DisplayName("장소 이름·지역·시군구·카테고리를 그 순서로 추천한다 (PLC-021, CMU-026, CMU-027)")
    void suggestsPlaceRegionCategory() {
        List<TagSuggestionResponse> suggestions = service.suggest(PLACE_ID, null, null);

        assertThat(suggestions).extracting(TagSuggestionResponse::name)
                .containsExactly("경복궁", "서울", "종로구", "관광지");
    }

    @Test
    @DisplayName("모르는 콘텐츠 유형 번호는 태그로 만들지 않는다 — 숫자가 해시태그로 붙는 것보다 낫다")
    void skipsUnknownContentType() {
        when(places.findById(PLACE_ID)).thenReturn(Optional.of(place(25)));

        assertThat(service.suggest(PLACE_ID, null, null))
                .extracting(TagSuggestionResponse::name)
                .containsExactly("경복궁", "서울", "종로구");
    }

    @Test
    @DisplayName("행사 고정 태그가 맨 앞이고 source 가 EVENT_FIXED 다 (CMU-028, EVT-018)")
    void eventFixedTagsComeFirst() {
        when(eventTags.fixedTagNames(EVENT_ID)).thenReturn(List.of("서울빛초롱축제"));

        List<TagSuggestionResponse> suggestions = service.suggest(PLACE_ID, EVENT_ID, null);

        assertThat(suggestions.get(0).name()).isEqualTo("서울빛초롱축제");
        assertThat(suggestions.get(0).source()).isEqualTo(TagSuggestionSource.EVENT_FIXED);
    }

    @Test
    @DisplayName("같은 이름이 고정과 장소로 겹치면 고정으로 남는다 — 뗄 수 없다는 성질이 더 중요하다")
    void fixedWinsOverPlaceName() {
        when(eventTags.fixedTagNames(EVENT_ID)).thenReturn(List.of("경복궁"));

        List<TagSuggestionResponse> suggestions = service.suggest(PLACE_ID, EVENT_ID, null);

        assertThat(suggestions).extracting(TagSuggestionResponse::name)
                .containsExactly("경복궁", "서울", "종로구", "관광지");
        assertThat(suggestions.get(0).source()).isEqualTo(TagSuggestionSource.EVENT_FIXED);
    }

    @Test
    @DisplayName("이미 있는 태그는 EXISTING 으로 tagId 를 주고, 없으면 NEW 에 null 이다")
    void marksExistingTags() {
        when(tags.findByNormalizedNameIn(any())).thenReturn(List.of(tag(7L, "서울")));

        List<TagSuggestionResponse> suggestions = service.suggest(PLACE_ID, null, null);

        assertThat(suggestions.get(0).source()).isEqualTo(TagSuggestionSource.NEW);
        assertThat(suggestions.get(0).tagId()).isNull();
        assertThat(suggestions.get(1).source()).isEqualTo(TagSuggestionSource.EXISTING);
        assertThat(suggestions.get(1).tagId()).isEqualTo("7");
    }

    @Test
    @DisplayName("추천 단계에서는 태그를 만들지 않는다 — 채택 안 된 추천이 마스터에 쌓이면 인기 집계가 오염된다")
    void doesNotCreateTags() {
        service.suggest(PLACE_ID, null, "드라마");

        verify(tags, never()).save(any());
        verify(tags, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("입력 접두어로 찾은 태그는 장소 추천 뒤에 붙는다")
    void appendsQueryMatches() {
        when(tags.findTop5ByNormalizedNameStartingWithOrderByUsageCountDesc("드라마"))
                .thenReturn(List.of(tag(3L, "드라마촬영지")));

        List<TagSuggestionResponse> suggestions = service.suggest(PLACE_ID, null, "드라마");

        assertThat(suggestions).extracting(TagSuggestionResponse::name)
                .containsExactly("경복궁", "서울", "종로구", "관광지", "드라마촬영지");
        assertThat(suggestions.get(4).source()).isEqualTo(TagSuggestionSource.EXISTING);
    }

    @Test
    @DisplayName("접두어가 비면 태그 검색을 하지 않는다")
    void skipsBlankQuery() {
        service.suggest(PLACE_ID, null, "   ");

        verify(tags, never()).findTop5ByNormalizedNameStartingWithOrderByUsageCountDesc(anyString());
    }

    @Test
    @DisplayName("없는 장소는 PLACE_NOT_FOUND")
    void missingPlace() {
        when(places.findById(PLACE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggest(PLACE_ID, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(((ApiException) t).errorCode())
                        .isEqualTo(ErrorCode.PLACE_NOT_FOUND));
    }

    @Test
    @DisplayName("등록 시점 채택 판정에 쓰는 집합은 정규화 이름이다 (CMU-029)")
    void suggestedNormalizedNames() {
        assertThat(service.suggestedNormalizedNames(PLACE_ID, null))
                .containsExactly("경복궁", "서울", "종로구", "관광지");
    }

    @Test
    @DisplayName("장소가 없는 게시글은 채택 판정 집합이 비어 있다 — 추천할 근거가 없다")
    void noPlaceMeansNoSuggestions() {
        assertThat(service.suggestedNormalizedNames(null, null)).isEmpty();
        verify(places, never()).findById(any());
    }
}
