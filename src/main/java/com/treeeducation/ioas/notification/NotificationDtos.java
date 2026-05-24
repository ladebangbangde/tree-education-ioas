package com.treeeducation.ioas.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class NotificationDtos {
    private NotificationDtos() {}

    @Schema(description = "发送站内通知请求")
    public record SendRequest(@NotNull Long receiverUserId,
                              String receiverRole,
                              @NotBlank String title,
                              String content,
                              @NotBlank String bizType,
                              Long bizId,
                              String actionUrl,
                              String notificationType,
                              Integer priority) {}

    @Schema(description = "站内通知响应")
    public record Response(Long id,
                           Long receiverUserId,
                           String receiverRole,
                           String title,
                           String content,
                           String bizType,
                           Long bizId,
                           String actionUrl,
                           String notificationType,
                           Integer priority,
                           String readStatus,
                           Instant readAt,
                           Instant createdAt) {
        public static Response of(NotificationMessage message) {
            return new Response(message.getId(), message.getReceiverUserId(), message.getReceiverRole(),
                    message.getTitle(), message.getContent(), message.getBizType(), message.getBizId(),
                    message.getActionUrl(), message.getNotificationType(), message.getPriority(),
                    message.getReadStatus(), message.getReadAt(), message.getCreatedAt());
        }
    }
}
