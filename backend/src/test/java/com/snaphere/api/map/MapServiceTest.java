package com.snaphere.api.map;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.place.PlaceDtos;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapServiceTest {
    @Test
    void 정상_화면_범위를_받는다() {
        assertThat(MapService.bounds(126.8, 37.4, 127.2, 37.8))
                .isEqualTo(new MapDtos.Bounds(126.8, 37.4, 127.2, 37.8));
    }

    @Test
    void 뒤집히거나_범위를_벗어난_화면을_거부한다() {
        assertThatThrownBy(() -> MapService.bounds(127, 37, 126, 38)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MapService.bounds(126, -91, 127, 38)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MapService.bounds(Double.NaN, 37, 127, 38)).isInstanceOf(ApiException.class);
    }

    @Test
    void 한시간_게시글이_다섯개_미만이면_하루로_폴백한다() {
        MapRepository repository = mock(MapRepository.class);
        MapCache cache = mock(MapCache.class);
        when(cache.get(any())).thenReturn(Optional.empty());
        when(repository.viewportPostCount(eq(MapPeriod.LAST_1H), any(), any())).thenReturn(4);
        when(repository.cells(eq(MapPeriod.LAST_24H), any(), any(), eq(501))).thenReturn(List.of());
        when(repository.nextRefreshAt(eq(MapPeriod.LAST_24H), any(), any())).thenReturn(OffsetDateTime.now());
        MapService service = service(repository, cache);

        MapDtos.HeatmapResult result = service.heatmap(126, 37, 127, 38, 10, "LAST_1H", false);

        assertThat(result.requestedPeriod()).isEqualTo(MapPeriod.LAST_1H);
        assertThat(result.effectivePeriod()).isEqualTo(MapPeriod.LAST_24H);
        assertThat(result.fallbackApplied()).isTrue();
    }

    @Test
    void 셀이_오백개를_넘으면_상위_오백개만_반환하고_로그_정규화한다() {
        MapRepository repository = mock(MapRepository.class);
        MapCache cache = mock(MapCache.class);
        when(cache.get(any())).thenReturn(Optional.empty());
        List<MapRepository.CellRow> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(new MapRepository.CellRow(MapPeriod.WEEKLY, 2, i, i, 37, 127,
                    501 - i, 0, 1, null, List.of(), List.of(), OffsetDateTime.now(), OffsetDateTime.now()));
        }
        when(repository.cells(eq(MapPeriod.WEEKLY), any(), any(), eq(501))).thenReturn(rows);
        when(repository.nextRefreshAt(eq(MapPeriod.WEEKLY), any(), any())).thenReturn(OffsetDateTime.now());
        MapDtos.HeatmapResult result = service(repository, cache)
                .heatmap(126, 37, 127, 38, 10, "WEEKLY", true);

        assertThat(result.cells()).hasSize(500);
        assertThat(result.truncated()).isTrue();
        assertThat(result.maxCount()).isEqualTo(501);
        assertThat(result.cells().getFirst().intensity()).isEqualTo(1d);
    }

    @Test
    void 대표_게시글이_없는_지역도_정상_반환한다() {
        MapRepository repository = mock(MapRepository.class);
        MapCache cache = mock(MapCache.class);
        PlaceDtos.Region region = new PlaceDtos.Region(1, "서울", "Seoul", null, 2000);
        when(repository.regions(MapPeriod.WEEKLY))
                .thenReturn(List.of(new MapRepository.RegionRow(region, 0, 0, null)));

        List<MapDtos.MapRegion> result = service(repository, cache)
                .regions("WEEKLY", Optional.empty());

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.region()).isEqualTo(region);
            assertThat(item.representativePost()).isNull();
        });
    }

    private static MapService service(MapRepository repository, MapCache cache) {
        return new MapService(repository, cache, mock(com.snaphere.api.post.repository.PostRepository.class),
                mock(com.snaphere.api.post.PostResponseAssembler.class),
                mock(com.snaphere.api.place.PlaceRepository.class));
    }
}
