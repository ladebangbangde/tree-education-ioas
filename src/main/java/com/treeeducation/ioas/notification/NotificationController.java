package com.treeeducation.ioas.notification;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "通用站内通知中心")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    @Operation(summary = "发送通用站内通知")
    public ApiResponse<NotificationDtos.Response> send(@Valid @RequestBody NotificationDtos.SendRequest request) {
        return ApiResponse.ok(NotificationDtos.Response.of(notificationService.sendToUser(request)));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的站内通知列表")
    public ApiResponse<PageResponse<NotificationDtos.Response>> mine(@RequestParam(required = false) String readStatus,
                                                                     @RequestParam(defaultValue = "1") int pageNum,
                                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                                     @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(notificationService.listMine(user.id(), readStatus, pageNum, pageSize));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "我的未读通知数")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(Map.of("unreadCount", notificationService.unreadCount(user.id())));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记单条通知为已读")
    public ApiResponse<NotificationDtos.Response> markRead(@PathVariable Long id,
                                                           @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(notificationService.markRead(user.id(), id));
    }

    @PostMapping("/read-all")
    @Operation(summary = "全部标记为已读")
    public ApiResponse<Map<String, Integer>> markAllRead(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(Map.of("updated", notificationService.markAllRead(user.id())));
    }
}
