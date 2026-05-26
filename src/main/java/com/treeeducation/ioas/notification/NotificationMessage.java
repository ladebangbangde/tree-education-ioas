package com.treeeducation.ioas.notification;

import jakarta.persistence.*;
import java.time.Instant;

/** Generic in-app notification message for all IOAS modules and roles. */
@Entity
@Table(name = "notification_message", indexes = {
        @Index(name = "idx_notification_receiver_read", columnList = "receiver_user_id,read_status"),
        @Index(name = "idx_notification_receiver_created", columnList = "receiver_user_id,created_at"),
        @Index(name = "idx_notification_biz", columnList = "biz_type,biz_id")
})
public class NotificationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiver_user_id", nullable = false)
    private Long receiverUserId;

    @Column(name = "receiver_role", length = 40)
    private String receiverRole;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 1000)
    private String content;

    @Column(name = "biz_type", nullable = false, length = 60)
    private String bizType;

    @Column(name = "biz_id")
    private Long bizId;

    @Column(name = "action_url", length = 300)
    private String actionUrl;

    @Column(name = "notification_type", nullable = false, length = 80)
    private String notificationType = "INFO";

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(name = "read_status", nullable = false, length = 20)
    private String readStatus = "UNREAD";

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

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
    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }
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
