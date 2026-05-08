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
UPDATE content_package
SET package_no = COALESCE(package_no, CONCAT('PKG', id)),
    topic_name = COALESCE(topic_name, name),
    folder_year = COALESCE(folder_year, YEAR(created_at)),
    folder_month = COALESCE(folder_month, MONTH(created_at)),
    folder_day = COALESCE(folder_day, DAY(created_at)),
    full_path = COALESCE(full_path, CONCAT('/', YEAR(created_at), '/', LPAD(MONTH(created_at), 2, '0'), '/', LPAD(DAY(created_at), 2, '0'), '/', COALESCE(name, CONCAT('package-', id)))),
    created_by_name = COALESCE(created_by_name, '系统迁移'),
    upload_status = CASE WHEN script_count + video_count + image_count = 0 THEN 'pending_upload' ELSE 'partial_completed' END;
ALTER TABLE content_package
  MODIFY COLUMN topic_name VARCHAR(160) NOT NULL,
  MODIFY COLUMN created_by_name VARCHAR(80) NOT NULL;

ALTER TABLE asset_file
  MODIFY COLUMN type VARCHAR(20) NULL,
  MODIFY COLUMN original_name VARCHAR(255) NULL,
  MODIFY COLUMN uploaded_by BIGINT NULL,
  ADD COLUMN file_no VARCHAR(40) NULL AFTER id,
  ADD COLUMN file_name VARCHAR(255) NULL AFTER package_id,
  ADD COLUMN file_type VARCHAR(20) NULL AFTER file_name,
  ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER preview_url,
  ADD COLUMN upload_status VARCHAR(40) NOT NULL DEFAULT 'success' AFTER sort_order,
  ADD COLUMN created_by BIGINT NULL AFTER upload_status,
  ADD COLUMN created_by_name VARCHAR(80) NULL AFTER created_by,
  ADD COLUMN created_at TIMESTAMP(6) NULL AFTER created_by_name,
  ADD COLUMN updated_at TIMESTAMP(6) NULL AFTER created_at,
  ADD INDEX idx_asset_file_type (file_type),
  ADD INDEX idx_asset_file_deleted (is_deleted);
UPDATE asset_file
SET file_no = COALESCE(file_no, CONCAT('FILE', id)),
    file_name = COALESCE(file_name, original_name),
    file_type = COALESCE(file_type, type),
    created_by = COALESCE(created_by, uploaded_by),
    created_by_name = COALESCE(created_by_name, '系统迁移'),
    created_at = COALESCE(created_at, uploaded_at),
    updated_at = COALESCE(updated_at, uploaded_at);
ALTER TABLE asset_file
  MODIFY COLUMN file_name VARCHAR(255) NOT NULL,
  MODIFY COLUMN file_type VARCHAR(20) NOT NULL,
  MODIFY COLUMN created_by BIGINT NOT NULL,
  MODIFY COLUMN created_by_name VARCHAR(80) NOT NULL,
  MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  MODIFY COLUMN updated_at TIMESTAMP(6) NULL;

ALTER TABLE lead_record
  MODIFY COLUMN package_id BIGINT NULL,
  MODIFY COLUMN asset_file_id BIGINT NULL,
  MODIFY COLUMN customer_name VARCHAR(120) NULL,
  MODIFY COLUMN created_by BIGINT NULL,
  ADD COLUMN lead_no VARCHAR(40) NULL AFTER id,
  ADD COLUMN source_type VARCHAR(40) NULL AFTER lead_no,
  ADD COLUMN related_package_id BIGINT NULL AFTER source_type,
  ADD COLUMN operator_id BIGINT NULL AFTER related_package_id,
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
UPDATE lead_record
SET lead_no = COALESCE(lead_no, CONCAT('LD', id)),
    source_type = COALESCE(source_type, 'content_package'),
    related_package_id = COALESCE(related_package_id, package_id),
    student_name = COALESCE(student_name, customer_name),
    status = CASE status WHEN 'new_lead' THEN 'unassigned' WHEN 'contacted' THEN 'following' WHEN 'converted' THEN 'completed' ELSE status END;
ALTER TABLE lead_record
  MODIFY COLUMN student_name VARCHAR(120) NOT NULL,
  MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'unassigned';

ALTER TABLE ioas_task
  MODIFY COLUMN type VARCHAR(20) NULL,
  MODIFY COLUMN title VARCHAR(160) NULL,
  ADD COLUMN task_type VARCHAR(40) NULL AFTER id,
  ADD COLUMN role_type VARCHAR(20) NULL AFTER task_type,
  ADD COLUMN related_package_id BIGINT NULL AFTER role_type,
  ADD COLUMN related_lead_id BIGINT NULL AFTER related_package_id,
  ADD COLUMN assignee_name VARCHAR(80) NULL AFTER assignee_id,
  ADD COLUMN progress INT NOT NULL DEFAULT 0 AFTER status,
  ADD COLUMN error_message VARCHAR(1000) NULL AFTER progress,
  ADD COLUMN completed_at TIMESTAMP(6) NULL AFTER error_message,
  ADD INDEX idx_task_role_status (role_type, status),
  ADD INDEX idx_task_related_package (related_package_id),
  ADD INDEX idx_task_related_lead (related_lead_id);
UPDATE ioas_task
SET task_type = COALESCE(task_type, CASE WHEN type = 'media' THEN 'media_upload' ELSE 'operator_lead_generate' END),
    role_type = COALESCE(role_type, CASE WHEN type = 'media' THEN 'media' ELSE 'operator' END),
    related_package_id = COALESCE(related_package_id, package_id),
    related_lead_id = COALESCE(related_lead_id, lead_id),
    status = CASE status WHEN 'in_progress' THEN 'processing' WHEN 'cancelled' THEN 'rejected' ELSE status END;
ALTER TABLE ioas_task
  MODIFY COLUMN task_type VARCHAR(40) NOT NULL,
  MODIFY COLUMN role_type VARCHAR(20) NOT NULL,
  MODIFY COLUMN status VARCHAR(40) NOT NULL DEFAULT 'pending';
