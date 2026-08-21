package com.ssafy.snaphere.domain.media.controller;

import com.ssafy.snaphere.domain.media.service.LocalMediaStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 로컬 미디어 저장소용 업로드·조회 엔드포인트.
 * S3 로 전환하면 이 컨트롤러는 필요 없어진다(클라이언트가 S3 로 직접 PUT 한다).
 *
 * ⚠️ 업로드 URL 은 인증 없이 PUT 을 받는다. 저장 키가 UUID 라 추측이 어렵고 TTL 이 짧지만,
 *    운영에서는 반드시 presigned URL 방식(S3)으로 바꿔야 한다.
 */
@Tag(name = "미디어 (로컬 개발용)")
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaFileController {

    private final LocalMediaStorage storage;

    @Operation(summary = "미디어 업로드 (발급받은 uploadUrl 로 PUT)")
    @PutMapping("/**")
    public ResponseEntity<Void> upload(HttpServletRequest request) throws IOException {
        String key = extractKey(request);
        try (var in = request.getInputStream()) {
            storage.store(key, in);
        }
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "미디어 조회")
    @GetMapping("/**")
    public ResponseEntity<PathResource> get(HttpServletRequest request) throws IOException {
        Path p = storage.load(extractKey(request));
        String contentType = Files.probeContentType(p);
        return ResponseEntity.ok()
                .contentType(contentType == null
                        ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(30)))
                .body(new PathResource(p));
    }

    private static String extractKey(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/media/");
        return idx < 0 ? "" : uri.substring(idx + "/media/".length());
    }
}
