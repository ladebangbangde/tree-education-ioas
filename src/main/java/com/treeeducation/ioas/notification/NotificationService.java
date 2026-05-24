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
    public NotificationMessage create(Long receiverUserId, String receiverRole, String title, String content,
                                      String bizType, Long bizId, String notificationType, Integer priority) {
        if (receiverUserId == null) {
            return null;
        }
        NotificationMessage message = new NotificationMessage();
        message.setReceiverUserId(receiverUserId);
        message.setReceiverRole(receiverRole);
        message.setTitle(title);
        message.setContent(content);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setNotificationType(notificationType == null ? "INFO" : notificationType);
        message.setPriority(priority == null ? 0 : priority);
        message.setReadStatus(READ_STATUS_UNREAD);
        return repository.save(message);
    }

    @Transactional
    public NotificationMessage createLeadAssignedNotification(Long receiverUserId, String receiverName, Long leadId,
                                                              String studentName, String targetCountry, String phone) {
        String title = "官网1分钟咨询新线索";
        String content = "你收到一条官网1分钟咨询线索：" + safe(studentName)
                + "，意向国家/地区：" + safe(targetCountry)
                + "，电话：" + safe(phone)
                + "。请尽快进入线索中心跟进。";
        return create(receiverUserId, "OPERATOR", title, content, "lead", leadId, "LEAD_ASSIGNED", 10);
    }

    public PageResponse<NotificationMessage> listMine(Long receiverUserId, String readStatus, int pageNum, int pageSize) {
        List<NotificationMessage> rows;
        if (readStatus == null || readStatus.isBlank()) {
            rows = repository.findByReceiverUserIdOrderByCreatedAtDesc(receiverUserId);
        } else {
            rows = repository.findByReceiverUserIdAndReadStatusOrderByCreatedAtDesc(receiverUserId, readStatus.trim().toUpperCase());
        }
        return PageResponse.of(rows, pageNum, pageSize);
    }

    public long unreadCount(Long receiverUserId) {
        return repository.countByReceiverUserIdAndReadStatus(receiverUserId, READ_STATUS_UNREAD);
    }

    @Transactional
    public NotificationMessage markRead(Long receiverUserId, Long notificationId) {
        NotificationMessage message = repository.findById(notificationId)
                .orElseThrow(() -> BusinessException.notFound("通知不存在"));
        if (!receiverUserId.equals(message.getReceiverUserId())) {
            throw BusinessException.forbidden("无权操作该通知");
        }
        if (!READ_STATUS_READ.equals(message.getReadStatus())) {
            message.setReadStatus(READ_STATUS_READ);
            message.setReadAt(Instant.now());
        }
        return repository.save(message);
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

    private String safe(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
    }
}
