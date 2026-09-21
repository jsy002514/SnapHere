package com.snaphere.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.time.LocalDate;

public final class BatchDtos {
    private BatchDtos() { }
    public record StartRequest(Integer areaCode, Integer contentTypeId) { }
    public record BatchRun(String runId, String jobType, String status, int processedCount,
                           int failedCount, OffsetDateTime startedAt) { }
    public record SyncLog(String syncId, String jobType, Integer areaCode, Integer contentTypeId,
                          String result, int count, String message, OffsetDateTime startedAt,
                          OffsetDateTime finishedAt) { }
    public record ModerationRequest(
            @NotBlank @Pattern(regexp = "RESTORE|HIDE_AND_REASSIGN") String action,
            String targetPlaceId) { }
    public record ResolveReportRequest(
            @NotBlank @Pattern(regexp = "RESTORE|HIDE|DELETE|REJECT") String action) { }
    public record ReportResult(String reportId, String reporterId, String targetType, String targetId,
                               String reason, String status, String action,
                               OffsetDateTime createdAt, OffsetDateTime reviewedAt) { }
    public record AdminEventRequest(@Size(max = 200) String title, String overview,
                                    LocalDate startDate, LocalDate endDate,
                                    @Min(1) @Max(20000) Integer verifyRadiusM) { }
}
