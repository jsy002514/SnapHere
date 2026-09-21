package com.snaphere.api.admin;

import com.snaphere.api.integration.TourApiClient;
import com.snaphere.api.place.PlaceRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.LocalDate;

/** TourAPI 행사와 행사장 장소를 지역별 독립 트랜잭션으로 동기화한다. (SYS-015, JOB-002) */
@Service
public class EventSyncWorker {
    private static final int PAGE_SIZE = 500;
    private final JdbcClient jdbc;
    private final TourApiClient tourApi;

    public EventSyncWorker(JdbcClient jdbc, TourApiClient tourApi) {
        this.jdbc = jdbc;
        this.tourApi = tourApi;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int syncArea(int requestedAreaCode, LocalDate from, LocalDate to) {
        int processed = 0;
        for (int page=1;;page++) {
            TourApiClient.FestivalPage response = tourApi.festivals(requestedAreaCode,from,to,page,PAGE_SIZE);
            for (TourApiClient.Festival event : response.items()) {
                if (event.startDate() == null || event.endDate() == null || event.title().isBlank()) continue;
                upsert(event,requestedAreaCode); processed++;
            }
            if (response.items().size() < PAGE_SIZE || processed >= response.totalCount()) break;
        }
        return processed;
    }

    private void upsert(TourApiClient.Festival event, int requestedAreaCode) {
        int areaCode = event.areaCode() == 0 ? requestedAreaCode : event.areaCode();
        long contentId = Long.parseLong(event.contentId());
        Long placeId = jdbc.sql("""
                INSERT INTO places(place_type,content_id,content_type_id,title,normalized_title,addr1,image_url,
                  lat,lng,verify_radius_m,area_code,sigungu_code,status)
                VALUES ('OFFICIAL',:content,15,:title,:normalized,:addr,:image,:lat,:lng,500,:area,
                  (SELECT sigungu_code FROM sigungu WHERE area_code=:area AND sigungu_code=:sigungu),'ACTIVE')
                ON CONFLICT(content_id,content_type_id) DO UPDATE SET
                  title=excluded.title,normalized_title=excluded.normalized_title,addr1=excluded.addr1,
                  image_url=excluded.image_url,lat=excluded.lat,lng=excluded.lng,area_code=excluded.area_code,
                  sigungu_code=excluded.sigungu_code,status='ACTIVE',updated_at=now()
                RETURNING place_id
                """).param("content",contentId).param("title",event.title())
                .param("normalized", PlaceRepository.normalizeTitle(event.title()))
                .param("addr",event.addr1()).param("image",event.imageUrl())
                .param("lat",event.lat(), Types.DOUBLE).param("lng",event.lng(),Types.DOUBLE)
                .param("area",areaCode).param("sigungu",event.sigunguCode(),Types.INTEGER)
                .query(Long.class).single();
        jdbc.sql("""
                INSERT INTO events(content_id,title,area_code,place_id,start_date,end_date,thumbnail_url,
                  fixed_tags,participant_count,source,status)
                VALUES (:content,:title,:area,:place,:start,:end,:image,
                  jsonb_build_array((SELECT name_ko FROM regions WHERE area_code=:area),:title),0,'TOURAPI','ACTIVE')
                ON CONFLICT(content_id) DO UPDATE SET title=excluded.title,area_code=excluded.area_code,
                  place_id=excluded.place_id,start_date=excluded.start_date,end_date=excluded.end_date,
                  thumbnail_url=excluded.thumbnail_url,fixed_tags=excluded.fixed_tags,source='TOURAPI',
                  status='ACTIVE',updated_at=now()
                """).param("content",event.contentId()).param("title",event.title()).param("area",areaCode)
                .param("place",placeId).param("start",event.startDate()).param("end",event.endDate())
                .param("image",event.imageUrl()).update();
    }

}
