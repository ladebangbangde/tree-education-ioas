CREATE TABLE IF NOT EXISTS data_operation_report_export_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_date DATE NOT NULL,
    platform VARCHAR(50) DEFAULT 'ALL',
    topic_package_id BIGINT NULL,
    only_confirmed TINYINT DEFAULT 1,
    file_name VARCHAR(255) NOT NULL,
    total_content_count INT DEFAULT 0,
    confirmed_count INT DEFAULT 0,
    unconfirmed_count INT DEFAULT