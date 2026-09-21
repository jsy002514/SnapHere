package com.snaphere.api.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 업로드할 파일 하나의 메타데이터.
 * 명세: 2. 요청 파라미터 > API-PST-001 > files
 */
public record PresignFileRequest(

        @NotBlank
        String mimeType,

        @Positive
        long sizeBytes
) {
}
