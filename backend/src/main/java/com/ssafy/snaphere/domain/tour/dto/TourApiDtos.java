package com.ssafy.snaphere.domain.tour.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TourApiDtos {

    private TourApiDtos() {}

    /** 한 페이지 결과. totalCount 로 다음 페이지 존재 여부를 판단한다. */
    public record Page<T>(List<T> items, int pageNo, int numOfRows, int totalCount) {
        public boolean hasNext() { return (long) pageNo * numOfRows < totalCount; }
    }

    /** areaCode2 — 시도(파라미터 없음) 또는 시군구(areaCode 지정) */
    public record AreaCodeItem(String code, String name) {}

    /**
     * areaBasedSyncList2 / searchFestival2 의 관광지 1건.
     *
     * ⚠️ mapx = 경도(lng), mapy = 위도(lat). 이름이 헷갈리는 지점이다.
     *    TourAPI 필드명을 그대로 두지 않고 여기서 lng/lat 으로 바꿔 담는다.
     */
    public record TourPlaceItem(
            Long contentId,
            Integer contentTypeId,
            String title,
            String addr1,
            String addr2,
            String zipcode,
            String tel,
            Integer areaCode,
            Integer sigunguCode,
            BigDecimal lat,              // mapy
            BigDecimal lng,              // mapx
            String cat1,
            String cat2,
            String cat3,
            String firstImageUrl,        // firstimage
            String firstImageThumb,      // firstimage2
            LocalDateTime tourModifiedAt, // modifiedtime (yyyyMMddHHmmss)
            boolean hidden,              // showflag = 0
            LocalDate eventStartDate,    // 축제(15) 전용
            LocalDate eventEndDate,
            String eventPlace,
            String organizer
    ) {
        public boolean hasCoordinate() { return lat != null && lng != null; }

        /** 제목이 없는 행은 적재하지 않는다. TourAPI 에 빈 title 이 실제로 존재한다. */
        public boolean isUsable() { return contentId != null && title != null && !title.isBlank(); }
    }
}
