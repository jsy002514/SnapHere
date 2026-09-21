package com.snaphere.api.post.dto;

import com.snaphere.api.post.tier.PhotoSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * 등급 미리보기 요청.
 * 명세: 2. 요청 파라미터 &gt; API-PST-002
 *
 * <p>위치 확인 단계에서 "이 사진이 어떤 등급으로 기록될지"를 미리 보여주기 위한 것이다 (PST-048).
 * 게시글을 만들지 않으므로 몇 번 호출해도 부작용이 없다.
 */
public record TierPreviewRequest(

        @NotNull
        Long placeId,

        Long eventId,

        @NotNull
        PhotoSource source,

        OffsetDateTime takenAt,

        @DecimalMin("-90") @DecimalMax("90")
        Double lat,

        @DecimalMin("-180") @DecimalMax("180")
        Double lng
) {
    /** 좌표는 둘 다 있거나 둘 다 없어야 한다. */
    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }
}
