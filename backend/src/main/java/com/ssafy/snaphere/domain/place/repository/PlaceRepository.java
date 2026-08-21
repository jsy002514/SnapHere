package com.ssafy.snaphere.domain.place.repository;

import com.ssafy.snaphere.domain.place.entity.Place;
import com.ssafy.snaphere.domain.place.entity.PlaceStatus;
import com.ssafy.snaphere.domain.place.entity.PlaceType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByContentId(Long contentId);

    Page<Place> findByAreaCodeAndStatus(Integer areaCode, PlaceStatus status, Pageable pageable);

    Page<Place> findByAreaCodeAndContentTypeIdAndStatus(
            Integer areaCode, Integer contentTypeId, PlaceStatus status, Pageable pageable);

    Page<Place> findByAreaCodeAndPlaceTypeAndStatus(
            Integer areaCode, PlaceType placeType, PlaceStatus status, Pageable pageable);

    /** 사용자 장소 하루 생성 한도(PLACE_002) 검사용 */
    @Query("""
            SELECT COUNT(p) FROM Place p
            WHERE p.createdByUserId = :userId AND p.createdAt >= :from
            """)
    long countCreatedSince(@Param("userId") Long userId,
                           @Param("from") java.time.LocalDateTime from);

    // ── 주변 검색 ──────────────────────────────────────────────
    /**
     * ★ 이 프로젝트에서 가장 조심해야 하는 쿼리.
     *
     *  1) POINT(:lng, :lat) — 경도가 먼저다. 바꾸면 "latitude out of range" 로 죽거나 엉뚱한 곳을 찾는다.
     *  2) MBRContains 절이 없으면 공간 인덱스를 못 타고 EXPLAIN type=ALL (풀스캔)이 된다.
     *     ST_Distance_Sphere 는 인덱스를 쓸 수 없으므로 "박스로 좁히고 → 정확 거리로 걸러내는" 2단 구조가 필수다.
     *  3) ST_Buffer 의 거리 단위는 SRID 4326 에서 **미터**다 (MySQL 8.0.46 에서 실측 확인).
     *     도(degree)로 착각해 radius/111320 을 넣으면 결과가 항상 0건이면서 에러도 안 난다.
     *
     *  radius 는 미터를 그대로 넘긴다.
     */
    @Query(value = """
            SELECT p.place_id            AS placeId,
                   p.place_type          AS placeType,
                   p.title               AS title,
                   p.content_type_id     AS contentTypeId,
                   p.first_image_thumb   AS thumbnailUrl,
                   p.lat                 AS lat,
                   p.lng                 AS lng,
                   p.verify_radius_m     AS verifyRadiusMeters,
                   p.post_count          AS postCount,
                   ST_Distance_Sphere(p.geom, ST_SRID(POINT(:lng, :lat), 4326)) AS distanceMeters
            FROM places p
            WHERE p.status = 'ACTIVE'
              AND p.has_coordinate = 1
              AND MBRContains(ST_Buffer(ST_SRID(POINT(:lng, :lat), 4326), :radius), p.geom)
              AND ST_Distance_Sphere(p.geom, ST_SRID(POINT(:lng, :lat), 4326)) <= :radius
              AND (:placeType     IS NULL OR p.place_type      = :placeType)
              AND (:contentTypeId IS NULL OR p.content_type_id = :contentTypeId)
              AND (:excludeId     IS NULL OR p.place_id       <> :excludeId)
            ORDER BY distanceMeters ASC, p.place_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyRow> findNearby(@Param("lat") double lat,
                               @Param("lng") double lng,
                               @Param("radius") int radius,
                               @Param("placeType") String placeType,
                               @Param("contentTypeId") Integer contentTypeId,
                               @Param("excludeId") Long excludeId,
                               @Param("limit") int limit);

    /**
     * 결과가 0건이어도 "가장 가까운 장소가 3.2km" 를 보여줘야 한다.
     * 그래서 반경 제한 없이 가장 가까운 1건의 거리만 구한다. 조회 범위를 20km 로 제한해 풀스캔을 막는다.
     */
    @Query(value = """
            SELECT MIN(ST_Distance_Sphere(p.geom, ST_SRID(POINT(:lng, :lat), 4326)))
            FROM places p
            WHERE p.status = 'ACTIVE'
              AND p.has_coordinate = 1
              AND MBRContains(ST_Buffer(ST_SRID(POINT(:lng, :lat), 4326), 20000), p.geom)
            """, nativeQuery = true)
    Double findNearestDistanceMeters(@Param("lat") double lat, @Param("lng") double lng);

    /** 사용자 장소 중복 방지 — 반경 안에 같은 이름이 이미 있는지 */
    @Query(value = """
            SELECT p.place_id
            FROM places p
            WHERE p.status = 'ACTIVE'
              AND p.has_coordinate = 1
              AND p.title = :title
              AND MBRContains(ST_Buffer(ST_SRID(POINT(:lng, :lat), 4326), :radius), p.geom)
              AND ST_Distance_Sphere(p.geom, ST_SRID(POINT(:lng, :lat), 4326)) <= :radius
            ORDER BY p.place_id ASC
            LIMIT 1
            """, nativeQuery = true)
    Long findDuplicateByTitleNear(@Param("title") String title,
                                  @Param("lat") double lat,
                                  @Param("lng") double lng,
                                  @Param("radius") int radius);

    /** 지도 마커 — 화면 영역(bounding box) 안의 장소. 상위 N개만. */
    @Query(value = """
            SELECT p.place_id          AS placeId,
                   p.place_type        AS placeType,
                   p.title             AS title,
                   p.content_type_id   AS contentTypeId,
                   p.first_image_thumb AS thumbnailUrl,
                   p.lat               AS lat,
                   p.lng               AS lng,
                   p.verify_radius_m   AS verifyRadiusMeters,
                   p.post_count        AS postCount,
                   0                   AS distanceMeters
            FROM places p
            WHERE p.status = 'ACTIVE'
              AND p.has_coordinate = 1
              AND p.lat BETWEEN :minLat AND :maxLat
              AND p.lng BETWEEN :minLng AND :maxLng
              AND (:contentTypeId IS NULL OR p.content_type_id = :contentTypeId)
            ORDER BY p.post_count DESC, p.place_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyRow> findMarkersInBounds(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                        @Param("minLng") double minLng, @Param("maxLng") double maxLng,
                                        @Param("contentTypeId") Integer contentTypeId,
                                        @Param("limit") int limit);

    @Query(value = """
            SELECT COUNT(*) FROM places p
            WHERE p.status = 'ACTIVE' AND p.has_coordinate = 1
              AND p.lat BETWEEN :minLat AND :maxLat
              AND p.lng BETWEEN :minLng AND :maxLng
              AND (:contentTypeId IS NULL OR p.content_type_id = :contentTypeId)
            """, nativeQuery = true)
    long countMarkersInBounds(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                              @Param("minLng") double minLng, @Param("maxLng") double maxLng,
                              @Param("contentTypeId") Integer contentTypeId);

    /** 장문 컬럼은 엔티티에 매핑하지 않는다. 상세 화면에서만 이걸로 따로 읽는다. */
    @Query(value = "SELECT p.overview AS overview, p.homepage AS homepage FROM places p WHERE p.place_id = :placeId",
            nativeQuery = true)
    Optional<LongTextRow> findLongTexts(@Param("placeId") Long placeId);

    @Query(value = "UPDATE places SET overview = :overview, overview_synced_at = NOW(6) WHERE place_id = :placeId",
            nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    void updateOverview(@Param("placeId") Long placeId, @Param("overview") String overview);

    /** 이벤트 탭 — 진행중·예정 행사 */
    @Query("""
            SELECT p FROM Place p
            WHERE p.status = com.ssafy.snaphere.domain.place.entity.PlaceStatus.ACTIVE
              AND p.contentTypeId = 15
              AND p.eventEndDate >= :today
              AND (:areaCode IS NULL OR p.areaCode = :areaCode)
            ORDER BY p.eventStartDate ASC, p.id ASC
            """)
    Page<Place> findEvents(@Param("today") LocalDate today,
                           @Param("areaCode") Integer areaCode,
                           Pageable pageable);

    /** 이름 검색 — ngram FULLTEXT. TourAPI 를 런타임 호출하지 않고 우리 DB 로 처리한다. */
    @Query(value = """
            SELECT p.place_id          AS placeId,
                   p.place_type        AS placeType,
                   p.title             AS title,
                   p.content_type_id   AS contentTypeId,
                   p.first_image_thumb AS thumbnailUrl,
                   p.lat               AS lat,
                   p.lng               AS lng,
                   p.verify_radius_m   AS verifyRadiusMeters,
                   p.post_count        AS postCount,
                   0                   AS distanceMeters
            FROM places p
            WHERE p.status = 'ACTIVE'
              AND MATCH(p.title, p.addr1) AGAINST (:keyword IN BOOLEAN MODE)
              AND (:areaCode IS NULL OR p.area_code = :areaCode)
            ORDER BY p.post_count DESC, p.place_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyRow> searchByKeyword(@Param("keyword") String keyword,
                                    @Param("areaCode") Integer areaCode,
                                    @Param("limit") int limit);

    /**
     * 자동 태그 추천용 — 좌표 반경 안에서 그 시각에 진행중인 행사.
     * 기간과 반경 두 조건을 모두 만족해야 추천한다. 하나만 맞으면 엉뚱한 축제 태그가 붙는다.
     */
    @Query(value = """
            SELECT p.place_id         AS placeId,
                   p.title            AS title,
                   p.event_start_date AS eventStartDate,
                   p.event_end_date   AS eventEndDate
            FROM places p
            WHERE p.status = 'ACTIVE'
              AND p.content_type_id = 15
              AND p.has_coordinate = 1
              AND p.event_start_date <= :on AND p.event_end_date >= :on
              AND MBRContains(ST_Buffer(ST_SRID(POINT(:lng, :lat), 4326), :radius), p.geom)
              AND ST_Distance_Sphere(p.geom, ST_SRID(POINT(:lng, :lat), 4326)) <= :radius
            ORDER BY p.post_count DESC, p.place_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<OngoingEventRow> findOngoingEventsNear(@Param("lat") double lat, @Param("lng") double lng,
                                                @Param("radius") int radius,
                                                @Param("on") java.time.LocalDate on,
                                                @Param("limit") int limit);

    interface OngoingEventRow {
        Long getPlaceId();
        String getTitle();
        java.sql.Date getEventStartDate();
        java.sql.Date getEventEndDate();
    }

    // ── nativeQuery 결과 projection ──
    interface NearbyRow {
        Long getPlaceId();
        String getPlaceType();
        String getTitle();
        Integer getContentTypeId();
        String getThumbnailUrl();
        java.math.BigDecimal getLat();
        java.math.BigDecimal getLng();
        Integer getVerifyRadiusMeters();
        Integer getPostCount();
        Double getDistanceMeters();
    }

    interface LongTextRow {
        String getOverview();
        String getHomepage();
    }
}
