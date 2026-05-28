CREATE TABLE application_flow (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  student_profile_id BIGINT NOT NULL,
  student_no VARCHAR(40),
  student_name VARCHAR(120) NOT NULL,
  owner_consultant_id BIGINT NOT NULL,
  owner_consultant_name VARCHAR(80) NOT NULL,
  current_step VARCHAR(40) NOT NULL,
  progress_percent INT NOT NULL DEFAULT 0,
  completed BIT NOT NULL DEFAULT 0,
  remark VARCHAR(1000),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6),
  version BIGINT
);

ALTER TABLE application_flow ADD CONSTRAINT uk_application_flow_student_profile UNIQUE (student_profile_id);

CREATE INDEX idx_application_flow_owner ON application_flow (owner_consultant_id);

CREATE TABLE application_flow_step (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  flow_id BIGINT NOT NULL,
  student_profile_id BIGINT NOT NULL,
  step_code VARCHAR(40) NOT NULL,
  order_no INT NOT NULL,
  step_name VARCHAR(120) NOT NULL,
  status VARCHAR(40) NOT NULL,
  required BIT NOT NULL DEFAULT 1,
  uploaded_file_count INT NOT NULL DEFAULT 0,
  consultant_note VARCHAR(1000),
  customer_visible_note VARCHAR(1000),
  started_at DATETIME(6),
  completed_at DATETIME(6),
  completed_by BIGINT,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6),
  version BIGINT
);

ALTER TABLE application_flow_step ADD CONSTRAINT uk_application_flow_step UNIQUE (flow_id, step_code);

CREATE INDEX idx_application_step_flow ON application_flow_step (flow_id);
CREATE INDEX idx_application_step_student ON application_flow_step (student_profile_id);

CREATE TABLE application_flow_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  flow_id BIGINT NOT NULL,
  step_id BIGINT NOT NULL,
  student_profile_id BIGINT NOT NULL,
  step_code VARCHAR(40) NOT NULL,
  attachment_type VARCHAR(60) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  object_key VARCHAR(1000) NOT NULL,
  file_url VARCHAR(1000),
  note VARCHAR(600),
  uploaded_by BIGINT NOT NULL,
  uploaded_by_name VARCHAR(80) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  deleted BIT NOT NULL DEFAULT 0,
  deleted_at DATETIME(6),
  deleted_by BIGINT
);

CREATE INDEX idx_application_attachment_flow ON application_flow_attachment (flow_id);
CREATE INDEX idx_application_attachment_step ON application_flow_attachment (step_id);
CREATE INDEX idx_application_attachment_student ON application_flow_attachment (student_profile_id);
