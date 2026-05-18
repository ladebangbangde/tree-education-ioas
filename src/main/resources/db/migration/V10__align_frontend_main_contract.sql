-- Contract alignment migration for tree-education-ioas-fronted main.
-- Keeps V1-V9 history intact while introducing frontend-aligned fields and values.

ALTER TABLE sys_user
  ADD COLUMN user_name VARCHAR(80) NULL AFTER display_name,
  ADD COLUMN department VARCHAR(80) NULL AFTER user_name;
UPDATE sys_role SET code = 'SUPER_ADMIN', name = '超级管理员' WHERE code = 'ADMIN';
UPDATE sys_user SET role_code = 'SUPER_ADMIN', user_name = display_name, department = '管理部' WHERE username = 'admin';
UPDATE sys_user SET user_name = display_name, department = '媒体部' WHERE username = 'media';
UPDATE sys_user SET user_name = display_name, department = '运营部' WHERE username = 'operator';
INSERT INTO sys_role(code, name) SELECT 'CONSULTANT', '顾问' WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'CONSULTANT');

ALTER TABLE content_package
  MODIFY COLUMN name VARCHAR(120) NULL,
  MODIFY COLUMN description VARCHAR(2000) NULL,
  MODIFY COLUMN status VARCHAR(20) NULL DEFAULT NULL,
  ADD COLUMN package_no VARCHAR(40) NULL AFTER id,
  ADD COLUMN topic_name VARCHAR(160) NULL AFTER package_no,
  ADD COLUMN operator_id BIGINT NULL AFTER topic_name,
  ADD COLUMN operator_name VARCHAR(80) NULL AFTER operator_id,
  ADD COLUMN folder_year INT NULL AFTER operator_name,
  ADD COLUMN folder_month INT NULL AFTER folder_year,
  ADD COLUMN folder_day INT NULL AFTER folder_month,
  ADD COLUMN full_path VARCHAR(600) NULL AFTER folder_day,
  ADD COLUMN cover_url VARCHAR(1000) NULL AFTER full_path,
  ADD COLUMN upload_status VARCHAR(40) NOT NULL DEFAULT 'pending_upload' AFTER image_count,
  ADD COLUMN created_by_name VARCHAR(80) NULL AFTER created_by,
  ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER updated_at,
  ADD COLUMN deleted_at TIMESTAMP(6) NULL AFTER is_deleted,
  ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at,
  ADD INDEX idx_content_package_operator (operator_id),
  ADD INDEX idx_content_package_upload_status (upload_status),
  ADD INDEX idx_content_package_deleted (is_deleted);

ALTER TABLE lead_record
  MODIFY COLUMN package_id BIGINT NULL,
  MODIFY COLUMN asset_file_id BIGINT NULL,
  MODIFY COLUMN customer_name VARCHAR(120) NULL,
  MODIFY COLUMN created_by BIGINT NULL,
  ADD COLUMN lead_no VARCHAR(40) NULL AFTER id,
  ADD COLUMN source_type VARCHAR(40) NULL AFTER lead_no,
  ADD COLUMN related_package_id BIGINT NULL AFTER source_type,
  ADD COLUMN student_name VARCHAR(120) NULL AFTER operator_id,
  ADD COLUMN wechat VARCHAR(80) NULL AFTER phone,
  ADD COLUMN source_channel VARCHAR(80) NULL AFTER wechat,
  ADD COLUMN target_country VARCHAR(80) NULL AFTER source_channel,
  ADD COLUMN target_major VARCHAR(120) NULL AFTER target_country,
  ADD COLUMN budget VARCHAR(80) NULL AFTER target_major,
  ADD COLUMN degree_level VARCHAR(80) NULL AFTER budget,
  ADD COLUMN assigned_to BIGINT NULL AFTER status,
  ADD COLUMN assigned_to_name VARCHAR(80) NULL AFTER assigned_to,
  ADD INDEX idx_lead_related_package (related_package_id),
  ADD INDEX idx_lead_status (status);
