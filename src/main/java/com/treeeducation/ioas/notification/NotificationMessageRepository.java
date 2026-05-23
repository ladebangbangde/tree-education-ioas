package com.treeeducation.ioas.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, Long> {
    long countByReceiverUserIdAndReadStatus(Long receiverUserId, String readStatus);
    List<NotificationMessage> findTop20ByReceiverUserIdOrderByCreatedAtDesc(Long receiverUserId);
}
