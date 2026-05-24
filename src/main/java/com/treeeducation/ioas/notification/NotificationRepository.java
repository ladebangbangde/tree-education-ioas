package com.treeeducation.ioas.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationMessage, Long> {
    long countByReceiverUserIdAndReadStatus(Long receiverUserId, String readStatus);
    List<NotificationMessage> findByReceiverUserIdOrderByCreatedAtDesc(Long receiverUserId);
    List<NotificationMessage> findByReceiverUserIdAndReadStatusOrderByCreatedAtDesc(Long receiverUserId, String readStatus);
    List<NotificationMessage> findTop10ByReceiverUserIdOrderByCreatedAtDesc(Long receiverUserId);
    List<NotificationMessage> findTop20ByReceiverUserIdOrderByCreatedAtDesc(Long receiverUserId);
}
