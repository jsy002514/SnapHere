package com.snaphere.api.admin;

import com.snaphere.api.integration.TourApiClient;
import com.snaphere.api.place.PlaceRepository;
import com.snaphere.api.place.PlaceReadCache;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Instant;

@Service
public class PlaceSyncWorker {
    private static final int PAGE_SIZE = 500;
    private final JdbcClient jdbc;
    private final TourApiClient tourApi;
    private final PlaceReadCache cache;

    public PlaceSyncWorker(JdbcClient jdbc, TourApiClient tourApi, PlaceReadCache cache) {
        this.jdbc = jdbc;
        this.tourApi = tourApi;
        this.cache = cache;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int syncCombination(int areaCode, int contentTypeId) {
        syncSigungu(areaCode);
        int processed = 0;
        for (int page = 1; ; page++) {
            TourApiClient.PlacePage response = tourApi.places(areaCode, contentTypeId, page, PAGE_SIZE);
            for (TourApiClient.OfficialPlace item : response.items()) {
                upsert(item); processed++;
            }
            if (response.items().size() < PAGE_SIZE || processed >= response.totalCount()) break;
        }
        return processed;
    }

    private void syncSigungu(int areaCode) {
        for (TourApiClient.CodeItem item : tourApi.sigungu(areaCode, "ko")) {
            jdbc.sql("""
                    INSERT INTO sigungu(area_code,sigungu_code,name_ko,name_en)
                    VALUES (:area,:code,:name,:name)
                    ON CONFLICT(area_code,sigungu_code) DO UPDATE SET name_ko=excluded.name_ko
                    """).param("area", areaCode).param("code", item.code()).param("name", item.name()).update();
        }
        cache.evictSigungu(areaCode);
    }

    private void upsert(TourApiClient.OfficialPlace item) {
        String sql = """
                INSERT INTO places(place_type,content_id,content_type_id,title,normalized_title,addr1,image_url,
                  lat,lng,verify_radius_m,area_code,sigungu_code,source_modified_at,status)
                VALUES ('OFFICIAL',:content,:type,:title,:normalized,:addr,:image,
                  :lat,:lng,500,:area,:sigungu,:modified,:status)
                ON CONFLICT(content_id,content_type_id) DO UPDATE SET
                  title=excluded.title,normalized_title=excluded.normalized_title,addr1=excluded.addr1,
                  image_url=excluded.image_url,lat=excluded.lat,lng=excluded.lng,area_code=excluded.area_code,
                  sigungu_code=excluded.sigungu_code,
                  source_modified_at=excluded.source_modified_at,status=excluded.status,updated_at=now()
                WHERE places.source_modified_at IS NULL OR excluded.source_modified_at IS NULL
                   OR excluded.source_modified_at > places.source_modified_at
                """;
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("content", Long.parseLong(item.contentId())).param("type", item.contentTypeId())
                .param("title", item.title()).param("normalized", PlaceRepository.normalizeTitle(item.title()))
                .param("addr", item.addr1()).param("image", item.imageUrl())
                .param("lat", item.lat(), Types.DOUBLE).param("lng", item.lng(), Types.DOUBLE)
                .param("area", item.areaCode()).param("sigungu", item.sigunguCode(), Types.INTEGER)
                .param("modified", item.modifiedAt(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("status", item.deleted() ? "DELETED" : "ACTIVE");
        spec.update();
    }
}
