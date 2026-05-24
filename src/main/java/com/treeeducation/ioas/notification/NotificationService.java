package com.treeeducation.ioas.notification;

import com.treeeducation.ioas.common.BusinessException;
import com.treeeducation.ioas.common.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {
    public static final String READ_STATUS_UNREAD = "UNREAD";
    public static final String READ_STATUS_READ = "READ";

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationMessage sendToUser(NotificationDtos.SendRequest request) {
        return sendToUser(request.receiverUserId(), request.receiverRole(), request.title(), request.content(),
                request.bizType(), request.bizId(), request.actionUrl(), request.notificationType(), request.priority());
    }

    @Transactional
    public NotificationMessage sendToUser(Long receiverUserId, String receiverRole, String title, String content,
                                          String bizType, Long bizId, String actionUrl,
                                          String notificationType, Integer priority) {
        if (receiverUserId == null) {
            return null;
        }
        NotificationMessage message = new NotificationMessage();
        message.setReceiverUserId(receiverUserId);
        message.setReceiverRole(blankToNull(receiverRole));
        message.setTitle(required(title, "通知标题不能为空"));
        message.setContent(blankToNull(content));
        message.setBizType(required(bizType, "业务类型不能为空"));
        message.setBizId(bizId);
        message.setActionUrl(blankToNull(actionUrl));
        message.setNotificationType(blankToDefault(notificationType, "INFO"));
        message.setPriority(priority == null ? 0 : priority);
        message.setReadStatus(READ_STATUS_UNREAD);
        return repository.save(message);
    }

    @Transactional
    public NotificationMessage create(Long receiverUserId, String receiverRole, String title, String content,
                                      String bizType, Long bizId, String notificationType, Integer priority) {
        return sendToUser(receiverUserId, receiverRole, title, content, bizType, bizId, null, notificationType, priority);
    }

    public PageResponse<NotificationDtos.Response> listMine(Long receiverUserId, String readStatus, int pageNum, int pageSize) {
        List<NotificationMessage> rows;
        if (readStatus == null || readStatus.isBlank()) {
            rows = repository.findByReceiverUserIdOrderByCreatedAtDesc(receiverUserId);
        } else {
            rows = repository.findByReceiverUserIdAndReadStatusOrderByCreatedAtDesc(receiverUserId, readStatus.trim().toUpperCase());
        }
        return PageResponse.of(rows.stream().map(NotificationDtos.Response::of).toList(), pageNum, pageSize);
    }

    public long unreadCount(Long receiverUserId) {
        return repository.countByReceiverUserIdAndReadStatus(receiverUserId, READ_STATUS_UNREAD);
    }

    @Transactional
    public NotificationDtos.Response markRead(Long receiverUserId, Long notificationId) {
        NotificationMessage message = repository.findById(notificationId)
                .orElseThrow(() -> BusinessException.notFound("通知不存在"));
        if (!receiverUserId.equals(message.getReceiverUserId())) {
            throw BusinessException.forbidden("无权操作该通知");
        }
        if (!READ_STATUS_READ.equals(message.getReadStatus())) {
            message.setReadStatus(READ_STATUS_READ);
            message.setReadAt(Instant.now());
        }
        return NotificationDtos.Response.of(repository.save(message));
    }

    @Transactional
    public int markAllRead(Long receiverUserId) {
        List<NotificationMessage> rows = repository.findByReceiverUserIdAndReadStatusOrderByCreatedAtDesc(receiverUserId, READ_STATUS_UNREAD);
        Instant now = Instant.now();
        rows.forEach(message -> {
            message.setReadStatus(READ_STATUS_READ);
            message.setReadAt(now);
        });
        repository.saveAll(rows);
        return rows.size();
    }

    private String required(String value, String message) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            throw BusinessException.badRequest(message);
        }
        return cleaned;
    }

    private String blankToDefault(String value, String defaultValue) {
        String cleaned = blankToNull(value);
        return cleaned == null ? defaultValue : cleaned;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
