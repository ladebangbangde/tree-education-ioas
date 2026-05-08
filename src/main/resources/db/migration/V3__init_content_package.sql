CREATE TABLE content_package (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(2000),
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  script_count INT NOT NULL DEFAULT 0,
  video_count INT NOT NULL DEFAULT 0,
  image_count INT NOT NULL DEFAULT 0,
  lead_count INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL,
  INDEX idx_content_package_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
