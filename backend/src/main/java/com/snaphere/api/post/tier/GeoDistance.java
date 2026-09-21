package com.snaphere.api.post.tier;

/**
 * 두 좌표 사이의 구면 거리(m). 하버사인 공식.
 *
 * <p>게시글 등록 경로에서는 PostGIS 의 {@code ST_DWithin}·{@code ST_Distance} 가 거리를 계산한다.
 * 이 클래스는 <b>DB 를 거치지 않는 등급 미리보기(API-PST-002)</b> 전용이다. (MAP-030, PST-048)
 * 두 계산 결과의 오차는 반경 판정(수백 m 단위)에 영향을 주지 않는다.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private GeoDistance() {
    }

    public static int meters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_M * c);
    }
}
