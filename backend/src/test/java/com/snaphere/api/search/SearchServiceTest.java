package com.snaphere.api.search;

import com.snaphere.api.auth.UserRepository;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.place.PlaceDtos;
import com.snaphere.api.post.PostResponseAssembler;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.repository.TagRepository;
import com.snaphere.api.social.FollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {
    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final List<PlaceDtos.Region> REGIONS = List.of(
            new PlaceDtos.Region(1, "서울", "Seoul", null, 2000),
            new PlaceDtos.Region(6, "부산", "Busan", null, 2000));

    private SearchRepository repository;
    private com.snaphere.api.place.PlaceRepository places;
    private RecentSearchStore recent;
    private PopularSearchCache popularCache;
    private SearchService service;

    @BeforeEach
    void setUp() {
        repository = mock(SearchRepository.class);
        places = mock(com.snaphere.api.place.PlaceRepository.class);
        recent = mock(RecentSearchStore.class);
        popularCache = mock(PopularSearchCache.class);
        when(places.regions()).thenReturn(REGIONS);
        when(repository.places(anyString(), anyBoolean(), any(), any(), anyInt())).thenReturn(List.of());
        when(repository.countPlaces(anyString(), anyBoolean(), any())).thenReturn(0L);

        service = new SearchService(repository, places, mock(PostRepository.class),
                mock(PostResponseAssembler.class), mock(UserRepository.class),
                mock(FollowRepository.class), mock(TagRepository.class), recent, popularCache);
    }

    @Test
    void exactRegionNameOverridesRequestedAreaAndRecordsFirstSearch() {
        SearchDtos.SearchResult result = service.search(" 서울특별시 ", List.of(SearchType.PLACE),
                6, null, 5, Optional.of(USER));

        assertThat(result.query()).isEqualTo("서울특별시");
        assertThat(result.matchedRegion().areaCode()).isEqualTo(1);
        verify(repository).places("서울특별시", true, 1, null, 6);
        verify(repository).record("서울특별시", 1);
        verify(recent).record(USER, "서울특별시", "서울특별시");
    }

    @Test
    void cursorRequiresExactlyOneTypeAndMatchingFingerprint() {
        String fingerprint = SearchCursor.fingerprint("경복궁", null, false);
        String cursor = new SearchCursor(SearchType.PLACE, fingerprint, 0, "1", "1", "1").encode();

        assertThatThrownBy(() -> service.search("경복궁",
                List.of(SearchType.PLACE, SearchType.POST), null, cursor, 5, Optional.empty()))
                .isInstanceOf(ApiException.class);
        verify(repository, never()).record(anyString(), any());
    }

    @Test
    void cursorPageDoesNotCreateAnotherSearchLog() {
        String fingerprint = SearchCursor.fingerprint("경복궁", null, false);
        String cursor = new SearchCursor(SearchType.PLACE, fingerprint, 0, "1", "1", "1").encode();

        service.search("경복궁", List.of(SearchType.PLACE), null, cursor, 5, Optional.empty());

        verify(repository, never()).record(anyString(), any());
        verify(recent, never()).record(any(), anyString(), anyString());
    }

    @Test
    void rejectsBlankLongAndUnknownAreaQueries() {
        assertThatThrownBy(() -> service.search("   ", null, null, null, null, Optional.empty()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.search("x".repeat(101), null, null, null, null, Optional.empty()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.search("경복궁", List.of(SearchType.PLACE), 999,
                null, null, Optional.empty())).isInstanceOf(ApiException.class);
    }
}
