package com.treeeducation.ioas.notification;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_message")
public class NotificationMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long receiverUserId;
    @Column(length = 40) private String receiverRole;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 1000) private String content;
    @Column(nullable = false, length = 60) private String bizType;
    private Long bizId;
    @Column(nullable = false, length = 30) private String notificationType = "INFO";
    @Column(nullable = false) private Integer priority = 0;
    @Column(nullable = false, length = 20) private String readStatus = "UNREAD";
    private Instant readAt;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReceiverUserId() { return receiverUserId; }
    public void setReceiverUserId(Long receiverUserId) { this.receiverUserId = receiverUserId; }
    public String getReceiverRole() { return receiverRole; }
    public void setReceiverRole(String receiverRole) { this.receiverRole = receiverRole; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getReadStatus() { return readStatus; }
    public void setReadStatus(String readStatus) { this.readStatus = readStatus; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
