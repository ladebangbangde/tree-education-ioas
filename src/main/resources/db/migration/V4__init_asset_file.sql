CREATE TABLE asset_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  package_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  bucket_name VARCHAR(100) NOT NULL,
  preview_url VARCHAR(1000),
  thumbnail_url VARCHAR(1000),
  mime_type VARCHAR(120) NOT NULL,
  file_size BIGINT NOT NULL,
  description VARCHAR(1000),
  uploaded_by BIGINT NOT NULL,
  uploaded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_asset_package (package_id),
  INDEX idx_asset_type (type),
  CONSTRAINT fk_asset_package FOREIGN KEY (package_id) REFERENCES content_package(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
