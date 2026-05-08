CREATE TABLE lead_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  package_id BIGINT NOT NULL,
  asset_file_id BIGINT NOT NULL,
  operator_id BIGINT,
  customer_name VARCHAR(120) NOT NULL,
  phone VARCHAR(40),
  remark VARCHAR(1000),
  status VARCHAR(30) NOT NULL DEFAULT 'new_lead',
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL,
  INDEX idx_lead_package (package_id), INDEX idx_lead_asset (asset_file_id), INDEX idx_lead_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
