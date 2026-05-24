package com.treeeducation.ioas.task;

import com.treeeducation.ioas.auth.UserPrincipal;
import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController("taskNotificationController")
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private static final List<String> ACTIVE_STATUSES = List.of("created", "queued", "uploading", "processing", "interrupted", "pending", "pending_supplement", "partial_success");

    private final TaskRepository taskRepository;

    public NotificationController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@AuthenticationPrincipal UserPrincipal p) {
        long active;
        if (p != null && "SUPER_ADMIN".equalsIgnoreCase(p.role())) {
            active = taskRepository.findAll().stream().filter(t -> ACTIVE_STATUSES.contains(t.getStatus())).count();
        } else {
            Long userId = p == null ? 0L : p.id();
            active = taskRepository.findAll().stream()
                    .filter(t -> ACTIVE_STATUSES.contains(t.getStatus()))
                    .filter(t -> t.getAssigneeId() == null || userId.equals(t.getAssigneeId()))
                    .count();
        }
        return ApiResponse.ok(Map.of("unreadCount", active, "activeTaskCount", active));
    }
}