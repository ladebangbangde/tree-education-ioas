package com.treeeducation.ioas.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationMessage create(Long receiverUserId, String receiverRole, String title, String content,
                                      String bizType, Long bizId, String notificationType, Integer priority) {
        NotificationMessage message = new NotificationMessage();
        message.setReceiverUserId(receiverUserId);
        message.setReceiverRole(receiverRole);
        message.setTitle(title);
        message.setContent(content);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setNotificationType(notificationType == null ? "INFO" : notificationType);
        message.setPriority(priority == null ? 0 : priority);
        return repository.save(message);
    }
}
