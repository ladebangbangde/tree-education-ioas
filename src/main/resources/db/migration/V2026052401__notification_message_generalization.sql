CREATE TABLE IF NOT EXISTS notification_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receiver_user_id BIGINT NOT NULL,
    receiver_role VARCHAR(40) NULL,
    title VARCHAR(160) NOT NULL,
    content VARCHAR(1000) NULL,
    biz_type VARCHAR(60) NOT NULL,
    biz_id BIGINT NULL,
    action_url VARCHAR(300) NULL,
    notification_type VARCHAR(30) NOT NULL DEFAULT 'INFO',
    priority INT NOT NULL DEFAULT 0,
    read_status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_notification_receiver_read (receiver_user_id, read_status),
    INDEX idx_notification_receiver_created (receiver_user_id, created_at),
    INDEX idx_notification_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @action_url_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notification_message'
      AND COLUMN_NAME = 'action_url'
);

SET @add_action_url_sql := IF(
    @action_url_column_exists = 0,
    'ALTER TABLE notification_message ADD COLUMN action_url VARCHAR(300) NULL AFTER biz_id',
    'SELECT 1'
);

PREPARE add_action_url_stmt FROM @add_action_url_sql;
EXECUTE add_action_url_stmt;
DEALLOCATE PREPARE add_action_url_stmt;
