package com.ssafy.snaphere.domain.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class MediaDtos {

    private MediaDtos() {}

    @Schema(name = "UploadUrlFileRequest")
    public record FileRequest(
            @NotBlank String fileName,
            @NotBlank String contentType,
            @Positive long fileSize,
            @Schema(description = "영상 길이(초). 이미지는 생략") Integer durationSec
    ) {}

    @Schema(name = "UploadUrlRequest")
    public record UploadUrlRequest(
            @NotEmpty @Size(max = 10) @Valid List<FileRequest> files
    ) {}

    @Schema(name = "UploadUrlItem")
    public record UploadUrlItem(
            @Schema(description = "이 URL 로 PUT 하면 서버를 거치지 않고 저장소에 바로 올라간다")
            String uploadUrl,
            String mediaKey,
            String mediaType,
            int expiresIn
    ) {}

    @Schema(name = "UploadUrlResponse")
    public record UploadUrlResponse(List<UploadUrlItem> items) {}
}
