package com.ssafy.snaphere.domain.notification.controller;

import com.ssafy.snaphere.domain.notification.service.NotificationService;
import com.ssafy.snaphere.global.common.ApiResponse;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.common.PageResponse;
import com.ssafy.snaphere.global.security.AuthUser;
import com.ssafy.snaphere.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "알림")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록",
               description = "messageKey + messageParams 를 준다. 문구는 앱이 다국어로 조립한다.")
    @GetMapping
    public ApiResponse<PageResponse<NotificationService.NotificationItem>> list(
            @AuthUser LoginUser me, @Valid PageRequestParam pageParam) {
        return ApiResponse.ok(notificationService.list(me.userId(), pageParam));
    }

    @Operation(summary = "안 읽은 알림 수 (탭 배지)")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount(@AuthUser LoginUser me) {
        return ApiResponse.ok(new UnreadCount(notificationService.unreadCount(me.userId())));
    }

    @Operation(summary = "읽음 처리", description = "ids 를 비우면 전체 읽음 처리")
    @PatchMapping("/read")
    public ApiResponse<ReadResult> read(@AuthUser LoginUser me,
                                        @RequestBody(required = false) ReadRequest req) {
        int updated = notificationService.markRead(me.userId(), req == null ? null : req.ids());
        return ApiResponse.ok(new ReadResult(updated));
    }

    public record UnreadCount(long unreadCount) {}
    public record ReadRequest(List<Long> ids) {}
    public record ReadResult(int updatedCount) {}
}
