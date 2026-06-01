CREATE TABLE IF NOT EXISTS data_operation_topic_package (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_no VARCHAR(40) NOT NULL UNIQUE,
    topic_date DATE NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    folder_name VARCHAR(500) NOT NULL,
    operator_user_ids VARCHAR(1000),
    operator_names VARCHAR(1000),
    media_user_ids VARCHAR(1000),
    media_names VARCHAR(1000),
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(80),
    status VARCHAR(40) NOT NULL DEFAULT 'draft',
    report_status VARCHAR(40) NOT NULL DEFAULT 'not_generated',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS data_operation_platform_topic (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_id BIGINT NOT NULL,
    platform_code VARCHAR(40) NOT NULL,
    platform_name VARCHAR(80) NOT NULL,
    sub_topic_name VARCHAR(300) NOT NULL,
    cover_asset_id BIGINT,
    cover_image_url VARCHAR(1000),
    ocr_status VARCHAR(40) NOT NULL DEFAULT 'pending',
    ocr_title VARCHAR(500),
    ocr_account_name VARCHAR(200),
    ocr_publish_time VARCHAR(80),
    ocr_payload_json TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'draft',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_data_platform_topic_package (package_id),
    INDEX idx_data_platform_topic_platform (platform_code)
);

CREATE TABLE IF NOT EXISTS data_operation_content (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_id BIGINT NOT NULL,
    platform_topic_id BIGINT NOT NULL,
    platform_code VARCHAR(40) NOT NULL,
    content_title VARCHAR(500) NOT NULL,
    content_summary VARCHAR(1000),
    content_date DATE,
    screenshot_count INT NOT NULL DEFAULT 0,
    recognition_status VARCHAR(40) NOT NULL DEFAULT 'pending',
    data_payload_json TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'draft',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_data_content_package (package_id),
    INDEX idx_data_content_topic (platform_topic_id),
    INDEX idx_data_content_platform (platform_code)
);

CREATE TABLE IF NOT EXISTS data_operation_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_id BIGINT,
    platform_topic_id BIGINT,
    content_id BIGINT,
    asset_type VARCHAR(40) NOT NULL,
    original_filename VARCHAR(500),
    bucket_name VARCHAR(120),
    object_key VARCHAR(1000),
    public_url VARCHAR(1000),
    mime_type VARCHAR(120),
    file_size BIGINT,
    upload_status VARCHAR(40) NOT NULL DEFAULT 'created',
    retry_count INT NOT NULL DEFAULT 0,
    task_id BIGINT,
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_data_asset_package (package_id),
    INDEX idx_data_asset_topic (platform_topic_id),
    INDEX idx_data_asset_content (content_id),
    INDEX idx_data_asset_task (task_id)
);

CREATE TABLE IF NOT EXISTS data_operation_daily_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_date DATE NOT NULL,
    package_count INT NOT NULL DEFAULT 0,
    content_count INT NOT NULL DEFAULT 0,
    screenshot_count INT NOT NULL DEFAULT 0,
    douyin_count INT NOT NULL DEFAULT 0,
    xiaohongshu_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    report_status VARCHAR(40) NOT NULL DEFAULT 'created',
    report_url VARCHAR(1000),
    summary_json TEXT,
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_data_report_date (report_date)
);
