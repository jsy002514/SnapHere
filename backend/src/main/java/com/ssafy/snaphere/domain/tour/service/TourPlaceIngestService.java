package com.ssafy.snaphere.domain.tour.service;

import com.ssafy.snaphere.domain.tour.client.TourApiClient;
import com.ssafy.snaphere.domain.tour.dto.TourApiDtos.Page;
import com.ssafy.snaphere.domain.tour.dto.TourApiDtos.TourPlaceItem;
import com.ssafy.snaphere.domain.tour.repository.TourUpsertRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "지역 × 콘텐츠유형" 한 조합을 적재한다.
 *
 * 왜 별도 빈으로 분리했나
 *   같은 클래스 안에서 @Transactional 메서드를 호출하면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다.
 *   조합 하나가 실패해도 나머지가 계속되도록 트랜잭션을 조합 단위로 끊어야 하므로 빈을 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourPlaceIngestService {

    /** 안전장치. 한 조합에서 이 페이지 수를 넘으면 중단한다(무한 루프·API 이상 응답 방지). */
    private static final int MAX_PAGES_PER_COMBINATION = 200;

    private final TourApiClient client;
    private final TourUpsertRepository upsertRepository;

    /** @return {신규, 기존, 스킵, API가 보고한 totalCount} */
    @Transactional
    public int[] ingestCombination(int areaCode, int contentTypeId) {
        int created = 0, updated = 0, skipped = 0, fetched = 0;

        for (int pageNo = 1; pageNo <= MAX_PAGES_PER_COMBINATION; pageNo++) {
            Page<TourPlaceItem> page = client.fetchPlacePage(areaCode, contentTypeId, pageNo);
            fetched = page.totalCount();
            if (page.items().isEmpty()) break;

            // 제목·contentId 가 없는 행은 버린다. TourAPI 에 실제로 존재한다.
            List<TourPlaceItem> usable = page.items().stream().filter(TourPlaceItem::isUsable).toList();
            skipped += page.items().size() - usable.size();

            int[] r = upsertRepository.upsertPlaces(usable);
            created += r[0];
            updated += r[1];

            if (!page.hasNext()) break;
            if (pageNo == MAX_PAGES_PER_COMBINATION) {
                log.warn("areaCode={} contentTypeId={} 페이지 상한({})에 도달해 중단. totalCount={}",
                        areaCode, contentTypeId, MAX_PAGES_PER_COMBINATION, page.totalCount());
            }
        }
        return new int[]{created, updated, skipped, fetched};
    }

    /**
     * 축제·행사 적재. 행사 기간이 필요하므로 searchFestival2 를 쓴다.
     * areaCode 를 지정하지 않으면 전국을 한 번에 받는다(호출 수 절약).
     */
    @Transactional
    public int[] ingestFestivals(String eventStartDate, Integer areaCode) {
        int created = 0, updated = 0, skipped = 0, fetched = 0;

        for (int pageNo = 1; pageNo <= MAX_PAGES_PER_COMBINATION; pageNo++) {
            Page<TourPlaceItem> page = client.fetchFestivalPage(eventStartDate, areaCode, pageNo);
            fetched = page.totalCount();
            if (page.items().isEmpty()) break;

            List<TourPlaceItem> usable = page.items().stream().filter(TourPlaceItem::isUsable).toList();
            skipped += page.items().size() - usable.size();

            int[] r = upsertRepository.upsertPlaces(usable);
            created += r[0];
            updated += r[1];

            if (!page.hasNext()) break;
        }
        return new int[]{created, updated, skipped, fetched};
    }
}
