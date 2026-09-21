package com.snaphere.api.report;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.TraceIdFilter;
import com.snaphere.api.report.dto.CreateReportRequest;
import com.snaphere.api.report.dto.ReportReceiptResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-PST-013 — 게시글 신고.
 *
 * <p>기능 명세: 5.5 관리 &gt; 신고
 * <p>요구사항: PST-043, PST-044, PST-045
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}/reports")
public class PostReportController {

    private final PostReportService postReportService;
    private final CurrentUserProvider currentUserProvider;

    public PostReportController(PostReportService postReportService,
                                CurrentUserProvider currentUserProvider) {
        this.postReportService = postReportService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReportReceiptResponse>> report(
            @PathVariable String postId,
            @Valid @RequestBody CreateReportRequest request,
            HttpServletRequest httpRequest) {

        CurrentUser user = currentUserProvider.require(httpRequest);
        ReportReceiptResponse receipt = postReportService.report(ExternalIds.parsePost(postId), user.userId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(receipt, TraceIdFilter.currentTraceId(httpRequest)));
    }
}
