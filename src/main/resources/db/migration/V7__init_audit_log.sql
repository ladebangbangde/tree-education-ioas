CREATE TABLE audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  action VARCHAR(40) NOT NULL,
  target_type VARCHAR(40) NOT NULL,
  target_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  detail VARCHAR(2000),
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_audit_target (target_type, target_id), INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
