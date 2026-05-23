CREATE TABLE IF NOT EXISTS consultant_profile (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  consultant_name VARCHAR(80) NOT NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(120) NULL,
  team_name VARCHAR(80) NULL,
  enabled BIT(1) NOT NULL DEFAULT b'1',
  assign_enabled BIT(1) NOT NULL DEFAULT b'1',
  max_daily_leads INT NOT NULL DEFAULT 30,
  current_daily_leads INT NOT NULL DEFAULT 0,
  last_assigned_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_consultant_profile_user_id (user_id),
  KEY idx_consultant_profile_assign (enabled, assign_enabled, current_daily_leads, last_assigned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultant_region (
  id BIGINT NOT NULL AUTO_INCREMENT,
  region_code VARCHAR(40) NOT NULL,
  region_name VARCHAR(80) NOT NULL,
  region_type VARCHAR(40) NOT NULL DEFAULT 'REGION',
  enabled BIT(1) NOT NULL DEFAULT b'1',
  sort_order INT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_consultant_region_code (region_code),
  KEY idx_consultant_region_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultant_region_relation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  consultant_id BIGINT NOT NULL,
  region_id BIGINT NOT NULL,
  enabled BIT(1) NOT NULL DEFAULT b'1',
  priority INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_consultant_region_relation (consultant_id, region_id),
  KEY idx_consultant_region_relation_region (region_id, enabled, priority),
  CONSTRAINT fk_consultant_region_relation_consultant FOREIGN KEY (consultant_id) REFERENCES consultant_profile(id),
  CONSTRAINT fk_consultant_region_relation_region FOREIGN KEY (region_id) REFERENCES consultant_region(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notification_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  receiver_user_id BIGINT NOT NULL,
  receiver_role VARCHAR(40) NULL,
  title VARCHAR(160) NOT NULL,
  content VARCHAR(1000) NULL,
  biz_type VARCHAR(60) NOT NULL,
  biz_id BIGINT NULL,
  notification_type VARCHAR(30) NOT NULL DEFAULT 'INFO',
  priority INT NOT NULL DEFAULT 0,
  read_status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
  read_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_notification_receiver_read (receiver_user_id, read_status, created_at),
  KEY idx_notification_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE lead_record
  ADD COLUMN intention_region_id BIGINT NULL,
  ADD COLUMN intention_region_code VARCHAR(40) NULL,
  ADD COLUMN intention_region_name VARCHAR(80) NULL,
  ADD COLUMN assign_mode VARCHAR(40) NULL,
  ADD COLUMN assign_reason VARCHAR(500) NULL,
  ADD COLUMN assigned_at DATETIME(6) NULL,
  ADD COLUMN notify_status VARCHAR(30) NULL,
  ADD COLUMN source_page VARCHAR(200) NULL;

INSERT IGNORE INTO consultant_region(region_code, region_name, region_type, enabled, sort_order, remark) VALUES
('AUSTRALIA', '澳大利亚', 'COUNTRY', b'1', 10, '默认留学目的地区域'),
('UK', '英国', 'COUNTRY', b'1', 20, '默认留学目的地区域'),
('EUROPE', '欧洲', 'REGION', b'1', 30, '默认留学目的地区域'),
('USA', '美国', 'COUNTRY', b'1', 40, '默认留学目的地区域');
