package com.ssafy.snaphere.global.util;

/**
 * 좌표 유틸. ★ 좌표에서 geom 을 만드는 코드는 여기에만 둔다.
 *
 * ⚠️ MySQL 8 SRID 4326 의 함정 (8.0.46 에서 직접 검증)
 *   · POINT() 함수 : POINT(경도, 위도)   →  ST_SRID(POINT(126.9770, 37.5796), 4326)
 *   · WKT  문자열  : 'POINT(위도 경도)'  →  ST_GeomFromText('POINT(37.5796 126.9770)', 4326)
 *   · ST_X(g) = 위도,  ST_Y(g) = 경도
 *   두 표기가 서로 반대다. 섞어 쓰면 거리 계산이 통째로 틀어진다.
 *
 * ⚠️ TourAPI 는 mapx = 경도, mapy = 위도 다 (이름이 헷갈리게 지어져 있음).
 *
 * ⚠️ 공간 인덱스를 타려면 nativeQuery 에 MBRContains 절이 반드시 있어야 한다.
 *     WHERE MBRContains(ST_Buffer(ST_SRID(POINT(:lng,:lat),4326), :radius), geom)
 *       AND ST_Distance_Sphere(geom, ST_SRID(POINT(:lng,:lat),4326)) <= :radius
 *   MBRContains 없이 ST_Distance_Sphere 만 쓰면 EXPLAIN type=ALL (풀스캔)이 된다.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_008.8;

    // 대한민국 대략 범위 (제주·울릉도 포함)
    private static final double MIN_LAT = 33.0, MAX_LAT = 38.7;
    private static final double MIN_LNG = 124.5, MAX_LNG = 132.0;

    private GeoUtils() {}

    /** nativeQuery 에 넣을 WKT. SRID 4326 이므로 (위도 경도) 순서. */
    public static String toWkt(double lat, double lng) {
        return "POINT(" + lat + " " + lng + ")";
    }

    public static boolean isValidLatLng(Double lat, Double lng) {
        return lat != null && lng != null
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    /** 서비스 범위(대한민국) 안인지 */
    public static boolean isInKorea(Double lat, Double lng) {
        return isValidLatLng(lat, lng)
                && lat >= MIN_LAT && lat <= MAX_LAT
                && lng >= MIN_LNG && lng <= MAX_LNG;
    }

    /** 두 좌표 사이 거리(m). DB 를 거치지 않고 검산할 때만 사용. */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 히트맵 격자 크기 — 줌 레벨로 결정 (docs/03_API명세서.md 표와 일치) */
    public static int gridLevelOf(int zoom) {
        if (zoom <= 6) return 0;
        if (zoom <= 9) return 1;
        if (zoom <= 12) return 2;
        return 3;
    }

    public static double gridSizeOf(int gridLevel) {
        return switch (gridLevel) {
            case 0 -> 1.0;
            case 1 -> 0.1;
            case 2 -> 0.01;
            default -> 0.001;
        };
    }
}
