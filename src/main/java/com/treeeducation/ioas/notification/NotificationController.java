package com.treeeducation.ioas.notification;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import com.treeeducation.ioas.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "站内通知")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/mine")
    @Operation(summary = "我的站内通知列表")
    public ApiResponse<PageResponse<NotificationMessage>> mine(@RequestParam(required = false) String readStatus,
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
    public ApiResponse<NotificationMessage> markRead(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(notificationService.markRead(user.id(), id));
    }

    @PostMapping("/read-all")
    @Operation(summary = "全部标记为已读")
    public ApiResponse<Map<String, Integer>> markAllRead(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(Map.of("updated", notificationService.markAllRead(user.id())));
    }
}
