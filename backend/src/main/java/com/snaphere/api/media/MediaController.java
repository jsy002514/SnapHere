package com.snaphere.api.media;

import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.TraceIdFilter;
import com.snaphere.api.media.dto.PresignRequest;
import com.snaphere.api.media.dto.UploadUrl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API-PST-001 — 이미지 업로드 URL 발급.
 *
 * <p>기능 명세: 2.3 사진·캡션·태그 &gt; 업로드 실행
 * <p>요구사항: PST-013, PST-014, PST-015, USER-004, SYS-020
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;
    private final CurrentUserProvider currentUserProvider;

    public MediaController(MediaService mediaService, CurrentUserProvider currentUserProvider) {
        this.mediaService = mediaService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/presigned-urls")
    public ResponseEntity<ApiResponse<List<UploadUrl>>> issuePresignedUrls(
            @Valid @RequestBody PresignRequest request,
            HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        List<UploadUrl> urls = mediaService.issueUploadUrls(user.userId(), request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.ok(urls, TraceIdFilter.currentTraceId(httpRequest)));
    }
}
