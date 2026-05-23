package com.treeeducation.ioas.notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, Long> {
    long countByReceiverUserIdAndReadStatus(Long receiverUserId, String readStatus);
}
