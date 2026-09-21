package com.snaphere.api.post;

import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.TraceIdFilter;
import com.snaphere.api.post.dto.TierPreviewRequest;
import com.snaphere.api.post.dto.TierResultResponse;
import com.snaphere.api.post.tier.TierDecision;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-PST-002 — 업로드 전 등급 미리보기.
 *
 * <p>기능 명세: 2.2 위치 확인 &gt; 등급 미리보기
 * <p>요구사항: PST-022 ~ PST-028, PST-046 ~ PST-049
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostTierController {

    private final TierPreviewService tierPreviewService;
    private final CurrentUserProvider currentUserProvider;

    public PostTierController(TierPreviewService tierPreviewService,
                              CurrentUserProvider currentUserProvider) {
        this.tierPreviewService = tierPreviewService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/tier-preview")
    public ResponseEntity<ApiResponse<TierResultResponse>> previewTier(
            @Valid @RequestBody TierPreviewRequest request,
            HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        TierDecision decision = tierPreviewService.preview(user.userId(), request);

        return ResponseEntity.ok(ApiResponse.ok(TierResultResponse.from(decision),
                TraceIdFilter.currentTraceId(httpRequest)));
    }
}
