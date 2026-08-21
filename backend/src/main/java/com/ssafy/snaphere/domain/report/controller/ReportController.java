package com.ssafy.snaphere.domain.report.controller;

import com.ssafy.snaphere.domain.report.service.ReportService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "신고")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    public record ReportRequest(@NotBlank String reason, String detail) {}

    @Operation(summary = "게시물 신고", description = "같은 대상 중복 신고는 REPORT_001. 3회 누적 시 야간 배치가 숨긴다.")
    @PostMapping("/posts/{postId}/reports")
    public ApiResponse<ReportService.Result> reportPost(@PathVariable Long postId,
                                                        @AuthUser LoginUser me,
                                                        @RequestBody ReportRequest req) {
        return ApiResponse.ok(reportService.report(me.userId(), "POST", postId, req.reason(), req.detail()));
    }

    @Operation(summary = "댓글 신고")
    @PostMapping("/comments/{commentId}/reports")
    public ApiResponse<ReportService.Result> reportComment(@PathVariable Long commentId,
                                                           @AuthUser LoginUser me,
                                                           @RequestBody ReportRequest req) {
        return ApiResponse.ok(reportService.report(me.userId(), "COMMENT", commentId, req.reason(), req.detail()));
    }

    @Operation(summary = "장소 신고 (중복 장소·잘못된 위치 등)")
    @PostMapping("/places/{placeId}/reports")
    public ApiResponse<ReportService.Result> reportPlace(@PathVariable Long placeId,
                                                          @AuthUser LoginUser me,
                                                          @RequestBody ReportRequest req) {
        return ApiResponse.ok(reportService.report(me.userId(), "PLACE", placeId, req.reason(), req.detail()));
    }
}
