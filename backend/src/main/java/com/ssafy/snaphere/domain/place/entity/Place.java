package com.ssafy.snaphere.domain.place.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관광지(OFFICIAL) + 사용자 장소(USER) 통합 엔티티.
 *
 * ⚠️ 일부러 매핑하지 않는 컬럼이 있다. 지우지 말고 이유를 읽을 것.
 *   · geom (POINT SRID 4326) — JPA 로 다루면 좌표 축 순서를 틀리기 쉽다.
 *     좌표 쓰기는 반드시 nativeQuery 의 ST_SRID(POINT(경도, 위도), 4326) 로만 한다.
 *   · overview (MEDIUMTEXT), homepage (TEXT) — 장문 컬럼.
 *     목록 조회에서 절대 딸려오면 안 되고, Hibernate 의 text 계열 타입 검증도 까다롭다.
 *     상세 화면에서만 PlaceRepository.findLongTexts() 로 따로 읽는다.
 */
@Getter
@Entity
@Table(name = "places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 30)
    private PlaceType placeType;

    // ── OFFICIAL (TourAPI) ──
    @Column(name = "content_id")       private Long contentId;
    @Column(name = "content_type_id")  private Integer contentTypeId;
    @Column(length = 10)               private String cat1;
    @Column(length = 10)               private String cat2;
    @Column(length = 10)               private String cat3;
    @Column(name = "tour_modified_at") private LocalDateTime tourModifiedAt;

    // ── 축제·행사 (content_type_id = 15) ──
    @Column(name = "event_start_date") private LocalDate eventStartDate;
    @Column(name = "event_end_date")   private LocalDate eventEndDate;
    @Column(name = "event_place", length = 255) private String eventPlace;
    @Column(length = 255)              private String organizer;
    @Column(name = "overview_synced_at") private LocalDateTime overviewSyncedAt;

    @Column(length = 100) private String tel;
    @Column(length = 20)  private String zipcode;

    // ── USER ──
    @Column(name = "created_by_user_id")   private Long createdByUserId;
    @Column(name = "merged_into_place_id") private Long mergedIntoPlaceId;

    // ── 공통 ──
    @Column(nullable = false, length = 255) private String title;
    @Column(length = 255) private String addr1;
    @Column(length = 255) private String addr2;

    @Column(name = "area_code", nullable = false) private Integer areaCode;
    @Column(name = "sigungu_code")                private Integer sigunguCode;

    @Column(precision = 10, scale = 7) private BigDecimal lat;
    @Column(precision = 10, scale = 7) private BigDecimal lng;

    @Column(name = "has_coordinate", nullable = false)  private boolean hasCoordinate;
    @Column(name = "verify_radius_m", nullable = false) private int verifyRadiusM;

    @Column(name = "first_image_url", length = 500)   private String firstImageUrl;
    @Column(name = "first_image_thumb", length = 500) private String firstImageThumb;

    @Column(name = "post_count", nullable = false)     private int postCount;
    @Column(name = "image_count", nullable = false)    private int imageCount;
    @Column(name = "like_count", nullable = false)     private int likeCount;
    @Column(name = "visit_count", nullable = false)    private int visitCount;
    @Column(name = "view_count", nullable = false)     private int viewCount;
    @Column(name = "bookmark_count", nullable = false) private int bookmarkCount;
    @Column(name = "is_featured", nullable = false)    private boolean featured;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlaceStatus status;

    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private LocalDateTime updatedAt;

    public boolean isActive()   { return status == PlaceStatus.ACTIVE; }

    /** 좌표를 쓰기 전 확인용. has_coordinate 플래그와 실제 값이 모두 있어야 한다. */
    public boolean isHasCoordinateSafe() {
        return hasCoordinate && lat != null && lng != null;
    }
    public boolean isOfficial() { return placeType == PlaceType.OFFICIAL; }

    /** 행사 진행 여부. 이벤트 탭과 자동 태그 추천이 쓴다. */
    public boolean isEventOngoing(LocalDate on) {
        if (eventStartDate == null || eventEndDate == null) return false;
        return !on.isBefore(eventStartDate) && !on.isAfter(eventEndDate);
    }

    /** 조회수는 상세 진입마다 1 증가. 카운터는 새벽 보정 배치가 정합성을 맞춘다. */
    public void increaseViewCount() { this.viewCount++; }

    public void markOverviewSynced() { this.overviewSyncedAt = LocalDateTime.now(); }
}
