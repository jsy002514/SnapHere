package com.snaphere.api.media.dto;

import com.snaphere.api.media.MediaPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Presigned URL 발급 요청.
 * 명세: 2. 요청 파라미터 > API-PST-001
 *
 * <p>개수 상한은 용도마다 달라서(게시글 4장 / 프로필 1장) 여기서는 비어 있지 않은지만 보고,
 * 실제 개수 검증은 {@link com.snaphere.api.media.MediaService} 가 한다. (PST-013)
 */
public record PresignRequest(

        @NotNull
        MediaPurpose purpose,

        @NotEmpty
        @Valid
        List<PresignFileRequest> files
) {
}
