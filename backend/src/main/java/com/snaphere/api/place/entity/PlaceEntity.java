package com.snaphere.api.place.entity;

import com.snaphere.api.place.PlaceSnapshot;
import com.snaphere.api.place.PlaceStatus;
import com.snaphere.api.place.PlaceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 관광지(OFFICIAL)와 사용자 등록 장소(USER)를 한 테이블에 담는다. (PLC-003)
 *
 * <p>지도와 주변탐색이 둘을 함께 그리므로 테이블을 나누면 모든 조회가 UNION 이 된다.
 *
 * <p><b>{@code geom} 과 {@code has_coordinate} 는 매핑하지 않는다.</b> 둘 다 {@code lat}·{@code lng}
 * 에서 DB 가 계산하는 생성 열이다. 자바에서 다시 들고 있으면 두 값이 어긋날 수 있고,
 * 생성 열은 INSERT 직후 엔티티에 반영되지도 않는다. 좌표 유무는 {@link #hasCoordinate()} 로 판단하고
 * 공간 검색은 {@code geom} 을 쓰는 네이티브 쿼리에 맡긴다.
 */
@Entity
@Table(name = "places")
public class PlaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 20)
    private PlaceType placeType;

    /** TourAPI contentId. 사용자 장소는 null 이다. */
    @Column(name = "content_id")
    private Long contentId;

    @Column(name = "content_type_id")
    private Integer contentTypeId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "addr1", length = 500)
    private String addr1;

    /** TourAPI mapy. */
    @Column(name = "lat")
    private Double lat;

    /** TourAPI mapx. */
    @Column(name = "lng")
    private Double lng;

    /** 관광지 500m · 사용자 장소 100m 기본. 관리자가 개별 조정할 수 있다. (PST-027, PLC-022) */
    @Column(name = "verify_radius_m", nullable = false)
    private Integer verifyRadiusM;

    @Column(name = "area_code", nullable = false)
    private Integer areaCode;

    @Column(name = "sigungu_code")
    private Integer sigunguCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlaceStatus status;

    @Column(name = "post_count", nullable = false)
    private int postCount;

    @Column(name = "visit_count", nullable = false)
    private int visitCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    /** 사용자 등록 장소의 작성자. */
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlaceEntity() {
    }

    /** 사용자가 등록한 숨은 명소. 기본 인증 반경은 100m 다. (PLC-014, PST-027) */
    public static PlaceEntity userPlace(String title, String addr1, double lat, double lng,
                                        int areaCode, Integer sigunguCode, UUID createdBy) {
        PlaceEntity place = new PlaceEntity();
        place.placeType = PlaceType.USER;
        place.title = title;
        place.addr1 = addr1;
        place.lat = lat;
        place.lng = lng;
        place.verifyRadiusM = PlaceType.USER.defaultVerifyRadiusM();
        place.areaCode = areaCode;
        place.sigunguCode = sigunguCode;
        place.status = PlaceStatus.ACTIVE;
        place.createdBy = createdBy;
        place.createdAt = place.updatedAt = OffsetDateTime.now();
        return place;
    }

    /** 좌표가 없으면 주변탐색·히트맵·등급 판정에서 제외한다. (PLC-007) */
    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }

    /** 등급 판정이 필요한 값만 뽑은 읽기 전용 뷰. */
    public PlaceSnapshot toSnapshot() {
        return new PlaceSnapshot(placeId, placeType, lat, lng, hasCoordinate(), verifyRadiusM, areaCode);
    }

    public Long getPlaceId() {
        return placeId;
    }

    public PlaceType getPlaceType() {
        return placeType;
    }

    public Long getContentId() {
        return contentId;
    }

    public Integer getContentTypeId() {
        return contentTypeId;
    }

    public String getTitle() {
        return title;
    }

    public String getAddr1() {
        return addr1;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }

    public Integer getVerifyRadiusM() {
        return verifyRadiusM;
    }

    public Integer getAreaCode() {
        return areaCode;
    }

    public Integer getSigunguCode() {
        return sigunguCode;
    }

    public PlaceStatus getStatus() {
        return status;
    }

    public int getPostCount() {
        return postCount;
    }

    public int getVisitCount() {
        return visitCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
