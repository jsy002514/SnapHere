package com.snaphere.api.post.tier;

import java.time.OffsetDateTime;

/**
 * 등급 판정 입력. 클라이언트 값은 그대로 믿지 않고 서버가 모은 값만 넣는다. (PST-022)
 *
 * @param source          사진 출처
 * @param takenAt         촬영 시각. EXIF 가 없으면 null
 * @param distanceM       장소 중심에서 촬영 좌표까지의 거리. 좌표가 없으면 null
 * @param appliedRadiusM  적용 인증 반경 (이벤트별 → 지역 기본 → 2,000m 또는 장소 기본)
 * @param placeHasCoordinate 장소 자체에 좌표가 있는가 (PLC-007)
 * @param decidedAt       판정 시각
 */
public record TierInput(
        PhotoSource source,
        OffsetDateTime takenAt,
        Integer distanceM,
        int appliedRadiusM,
        boolean placeHasCoordinate,
        OffsetDateTime decidedAt
) {
}
