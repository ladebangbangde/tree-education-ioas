CREATE TABLE official_lead (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    age VARCHAR(20) NOT NULL,
    education VARCHAR(120) NOT NULL,
    city VARCHAR(120) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    wechat VARCHAR(80) NULL,
    destination VARCHAR(120) NOT NULL,
    budget VARCHAR(80) NOT NULL,
    remark TEXT NULL,
    source VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_official_lead_phone (phone),
    INDEX idx_official_lead_status (status),
    INDEX idx_official_lead_created_at (created_at)
);
