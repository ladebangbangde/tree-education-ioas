CREATE TABLE ioas_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'pending',
  title VARCHAR(160) NOT NULL,
  package_id BIGINT,
  asset_file_id BIGINT,
  lead_id BIGINT,
  assignee_id BIGINT,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL,
  INDEX idx_task_type_status (type, status), INDEX idx_task_package (package_id), INDEX idx_task_assignee (assignee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
