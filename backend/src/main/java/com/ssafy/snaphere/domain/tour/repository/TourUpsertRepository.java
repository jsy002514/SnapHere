package com.ssafy.snaphere.domain.tour.repository;

import com.ssafy.snaphere.domain.tour.dto.TourApiDtos.AreaCodeItem;
import com.ssafy.snaphere.domain.tour.dto.TourApiDtos.TourPlaceItem;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * TourAPI 적재 전용 벌크 UPSERT.
 *
 * JPA 엔티티를 쓰지 않는 이유
 *  1) 수만 건을 넣을 때 "먼저 SELECT 해서 있는지 보고 INSERT/UPDATE" 를 하면 쿼리가 2배가 된다.
 *  2) geom(POINT SRID 4326) 은 JPA 로 다루기 번거롭고, SQL 로 ST_SRID(POINT(...)) 를 쓰는 게 정확하다.
 *  3) "TourAPI 쪽 수정시각이 더 최신일 때만 갱신" 이라는 조건을 ON DUPLICATE KEY UPDATE 한 방에 담을 수 있다.
 *
 * ⚠️ 좌표 축 순서 — MySQL POINT() 함수는 (경도, 위도) 순서다. WKT 문자열과 반대다.
 *    ST_SRID(POINT(lng, lat), 4326)  ← 이 순서를 바꾸면 "latitude out of range" 로 INSERT 가 실패한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TourUpsertRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 좌표가 없는 장소도 저장해야 한다. geom 은 NOT NULL 이라 POINT(0,0) 을 넣고 has_coordinate=0 으로 표시한다. */
    private static final String NO_COORD_LNG = "0";
    private static final String NO_COORD_LAT = "0";

    // MySQL 8.0.19+ 의 별칭 문법. VALUES() 함수는 8.0.20 부터 deprecated 이므로 쓰지 않는다.
    private static final String UPSERT_PLACE = """
            INSERT INTO places (
                place_type, content_id, content_type_id, cat1, cat2, cat3, tour_modified_at,
                event_start_date, event_end_date, event_place, organizer,
                tel, zipcode, title, addr1, addr2, area_code, sigungu_code,
                lat, lng, geom, has_coordinate,
                first_image_url, first_image_thumb, status
            ) VALUES (
                'OFFICIAL', ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ST_SRID(POINT(?, ?), 4326), ?,
                ?, ?, ?
            ) AS incoming
            ON DUPLICATE KEY UPDATE
                content_type_id   = IF(%1$s, incoming.content_type_id,   places.content_type_id),
                cat1              = IF(%1$s, incoming.cat1,              places.cat1),
                cat2              = IF(%1$s, incoming.cat2,              places.cat2),
                cat3              = IF(%1$s, incoming.cat3,              places.cat3),
                event_start_date  = IF(%1$s, incoming.event_start_date,  places.event_start_date),
                event_end_date    = IF(%1$s, incoming.event_end_date,    places.event_end_date),
                event_place       = IF(%1$s, incoming.event_place,       places.event_place),
                organizer         = IF(%1$s, incoming.organizer,         places.organizer),
                tel               = IF(%1$s, incoming.tel,               places.tel),
                zipcode           = IF(%1$s, incoming.zipcode,           places.zipcode),
                title             = IF(%1$s, incoming.title,             places.title),
                addr1             = IF(%1$s, incoming.addr1,             places.addr1),
                addr2             = IF(%1$s, incoming.addr2,             places.addr2),
                area_code         = IF(%1$s, incoming.area_code,         places.area_code),
                sigungu_code      = IF(%1$s, incoming.sigungu_code,      places.sigungu_code),
                lat               = IF(%1$s, incoming.lat,               places.lat),
                lng               = IF(%1$s, incoming.lng,               places.lng),
                geom              = IF(%1$s, incoming.geom,              places.geom),
                has_coordinate    = IF(%1$s, incoming.has_coordinate,    places.has_coordinate),
                first_image_url   = IF(%1$s, incoming.first_image_url,   places.first_image_url),
                first_image_thumb = IF(%1$s, incoming.first_image_thumb, places.first_image_thumb),
                status            = IF(%1$s, incoming.status,            places.status),
                tour_modified_at  = IF(%1$s, incoming.tour_modified_at,  places.tour_modified_at)
            """.formatted(
            // 갱신 조건: 기존 수정시각이 없거나, 들어온 쪽이 더 최신일 때만 덮어쓴다.
            "places.tour_modified_at IS NULL "
                    + "OR incoming.tour_modified_at IS NULL "
                    + "OR incoming.tour_modified_at >= places.tour_modified_at");

    /**
     * @return {신규 건수, 기존 건수}
     *
     * ⚠️ 배치 반환값으로 신규/갱신을 세면 안 된다.
     *    우리 JDBC URL 에 rewriteBatchedStatements=true 가 켜져 있어서 MySQL 이 배치를
     *    하나의 다중행 INSERT 로 재작성한다. 그러면 행별 결과가 실제 영향 행수가 아니라
     *    Statement.SUCCESS_NO_INFO(-2) 로 돌아와 "1 이면 신규, 2 이면 갱신" 판별이 전부 실패한다.
     *    (2026-08-22 실제로 이 버그로 595건을 넣고도 0건으로 집계됐다.)
     *
     *    그래서 넣기 전에 이미 존재하는 content_id 를 한 번 조회해서 정확히 센다.
     *    쿼리 1회 추가 비용으로 집계가 정확해지고, 배치 성능 최적화는 그대로 유지된다.
     */
    public int[] upsertPlaces(List<TourPlaceItem> items) {
        if (items.isEmpty()) return new int[]{0, 0};

        List<Long> contentIds = items.stream().map(TourPlaceItem::contentId).filter(java.util.Objects::nonNull).toList();
        int existing = countExisting(contentIds);
        int created = items.size() - existing;

        int[] results = jdbcTemplate.batchUpdate(UPSERT_PLACE, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                bind(ps, items.get(i));
            }
            @Override
            public int getBatchSize() { return items.size(); }
        });

        int failed = 0;
        for (int r : results) {
            if (r == java.sql.Statement.EXECUTE_FAILED) failed++;
        }
        if (failed > 0) log.warn("배치 중 실패한 행 {}건 (전체 {}건)", failed, items.size());

        return new int[]{created, existing};
    }

    /** 이 content_id 들 중 이미 저장돼 있는 건수. IN 절이 너무 길어지지 않게 나눠 조회한다. */
    private int countExisting(List<Long> contentIds) {
        if (contentIds.isEmpty()) return 0;
        final int CHUNK = 500;
        int total = 0;
        for (int from = 0; from < contentIds.size(); from += CHUNK) {
            List<Long> chunk = contentIds.subList(from, Math.min(from + CHUNK, contentIds.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM places WHERE content_id IN (" + placeholders + ")",
                    Integer.class, chunk.toArray());
            total += n == null ? 0 : n;
        }
        return total;
    }

    private void bind(PreparedStatement ps, TourPlaceItem it) throws SQLException {
        int i = 1;
        ps.setLong(i++, it.contentId());
        setInt(ps, i++, it.contentTypeId());
        setStr(ps, i++, it.cat1());
        setStr(ps, i++, it.cat2());
        setStr(ps, i++, it.cat3());
        if (it.tourModifiedAt() == null) ps.setNull(i++, Types.TIMESTAMP);
        else ps.setTimestamp(i++, java.sql.Timestamp.valueOf(it.tourModifiedAt()));

        if (it.eventStartDate() == null) ps.setNull(i++, Types.DATE);
        else ps.setDate(i++, java.sql.Date.valueOf(it.eventStartDate()));
        if (it.eventEndDate() == null) ps.setNull(i++, Types.DATE);
        else ps.setDate(i++, java.sql.Date.valueOf(it.eventEndDate()));
        setStr(ps, i++, it.eventPlace());
        setStr(ps, i++, it.organizer());

        setStr(ps, i++, it.tel());
        setStr(ps, i++, it.zipcode());
        ps.setString(i++, it.title());
        setStr(ps, i++, it.addr1());
        setStr(ps, i++, it.addr2());
        ps.setInt(i++, it.areaCode());
        setInt(ps, i++, it.sigunguCode());

        // lat / lng 컬럼
        setDecimal(ps, i++, it.lat());
        setDecimal(ps, i++, it.lng());
        // ⚠️ POINT(경도, 위도) — 순서 주의
        ps.setString(i++, it.hasCoordinate() ? it.lng().toPlainString() : NO_COORD_LNG);
        ps.setString(i++, it.hasCoordinate() ? it.lat().toPlainString() : NO_COORD_LAT);
        ps.setInt(i++, it.hasCoordinate() ? 1 : 0);

        setStr(ps, i++, it.firstImageUrl());
        setStr(ps, i++, it.firstImageThumb());
        // showflag=0 이면 우리 쪽에서도 숨긴다
        ps.setString(i, it.hidden() ? "HIDDEN" : "ACTIVE");
    }

    private static void setStr(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.VARCHAR); else ps.setString(idx, v);
    }

    private static void setInt(PreparedStatement ps, int idx, Integer v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.INTEGER); else ps.setInt(idx, v);
    }

    private static void setDecimal(PreparedStatement ps, int idx, BigDecimal v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.DECIMAL); else ps.setBigDecimal(idx, v);
    }

    // ── 지역 / 시군구 ────────────────────────────────────────────

    /**
     * 지역 마스터 검증. **regions.name_ko 를 덮어쓰지 않는다.**
     *
     * ⚠️ 예전에는 여기서 TourAPI 가 준 이름으로 name_ko 를 UPDATE 했다.
     *    (참고: 2026-08-22 에 지역명이 '??' 로 깨진 사고의 원인은 이 코드가 아니라
     *     PowerShell 파이프의 ASCII 인코딩이었다 — CLAUDE.md 함정 2-4 참고.
     *     다만 아래 이유로 덮어쓰기 자체가 잘못된 설계라 이 기회에 없앴다.)
     *
     * 왜 덮어쓰면 안 되는가
     *   · 다국어 이름(name_en/ja/zh)과 라벨 좌표는 우리가 직접 관리한다. TourAPI 에는 그 값이 없다.
     *   · TourAPI 표기가 바뀌면(예: "전북" ↔ "전라북도") 우리 화면과 태그가 소리 없이 흔들린다.
     *   · area_code 는 절대 변하지 않는 코드값이라, 이름을 동기화할 이유 자체가 없다.
     *
     * 그래서 이 메서드는 **검증만** 한다. TourAPI 에 있는 지역이 우리 DB 에 없으면 경고를 남긴다.
     * 지역 이름의 원본은 `docs/04_seed_regions.sql` 이다.
     *
     * @return 우리 DB 에 존재하는 것으로 확인된 지역 수
     */
    public int verifyRegions(List<AreaCodeItem> items) {
        int matched = 0;
        for (AreaCodeItem it : items) {
            Integer code = parseInt(it.code());
            if (code == null) continue;
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM regions WHERE area_code = ?", Integer.class, code);
            if (exists != null && exists > 0) {
                matched++;
            } else {
                log.warn("TourAPI 에 있는 지역이 우리 DB 에 없습니다. areaCode={} name={} "
                        + "— docs/04_seed_regions.sql 을 확인하세요.", code, it.name());
            }
        }
        return matched;
    }

    public int upsertSigungu(int areaCode, List<AreaCodeItem> items) {
        int n = 0;
        for (AreaCodeItem it : items) {
            Integer code = parseInt(it.code());
            if (code == null || it.name() == null) continue;
            n += jdbcTemplate.update("""
                    INSERT INTO sigungu (area_code, sigungu_code, name_ko)
                    VALUES (?, ?, ?) AS incoming
                    ON DUPLICATE KEY UPDATE name_ko = incoming.name_ko
                    """, areaCode, code, it.name());
        }
        return n;
    }

    private static Integer parseInt(String s) {
        try { return s == null ? null : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
