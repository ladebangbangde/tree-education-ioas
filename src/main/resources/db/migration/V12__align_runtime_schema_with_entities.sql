-- Align runtime schema with current JPA entities.
-- MySQL 5.7 does not support ADD COLUMN IF NOT EXISTS, so use metadata guarded DDL.

DELIMITER $$

CREATE PROCEDURE add_column_if_missing(
  IN p_table_name VARCHAR(64),
  IN p_column_name VARCHAR(64),
  IN p_column_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND column_name = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

CREATE PROCEDURE add_index_if_missing(
  IN p_table_name VARCHAR(64),
  IN p_index_name VARCHAR(64),
  IN p_index_columns TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND index_name = p_index_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE ', p_table_name, ' ADD INDEX ', p_index_name, ' (', p_index_columns, ')');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

DELIMITER ;

-- content_package fields required by ContentPackage entity
CALL add_column_if_missing('content_package', 'package_no', 'VARCHAR(40) NULL AFTER id');
CALL add_column_if_missing('content_package', 'topic_name', 'VARCHAR(160) NULL AFTER package_no');
CALL add_column_if_missing('content_package', 'operator_id', 'BIGINT NULL AFTER topic_name');
CALL add_column_if_missing('content_package', 'operator_name', 'VARCHAR(80) NULL AFTER operator_id');
CALL add_column_if_missing('content_package', 'folder_year', 'INT NULL AFTER operator_name');
CALL add_column_if_missing('content_package', 'folder_month', 'INT NULL AFTER folder_year');
CALL add_column_if_missing('content_package', 'folder_day', 'INT NULL AFTER folder_month');
CALL add_column_if_missing('content_package', 'full_path', 'VARCHAR(600) NULL AFTER folder_day');
CALL add_column_if_missing('content_package', 'cover_url', 'VARCHAR(1000) NULL AFTER full_path');
CALL add_column_if_missing('content_package', 'upload_status', 'VARCHAR(40) NOT NULL DEFAULT ''pending_upload'' AFTER image_count');
CALL add_column_if_missing('content_package', 'created_by_name', 'VARCHAR(80) NULL AFTER created_by');
CALL add_column_if_missing('content_package', 'is_deleted', 'BOOLEAN NOT NULL DEFAULT FALSE AFTER updated_at');
CALL add_column_if_missing('content_package', 'deleted_at', 'TIMESTAMP(6) NULL AFTER is_deleted');
CALL add_column_if_missing('content_package', 'deleted_by', 'BIGINT NULL AFTER deleted_at');

UPDATE content_package
SET package_no = COALESCE(package_no, CONCAT('PKG', id)),
    topic_name = COALESCE(topic_name, name, CONCAT('package-', id)),
    folder_year = COALESCE(folder_year, YEAR(created_at)),
    folder_month = COALESCE(folder_month, MONTH(created_at)),
    folder_day = COALESCE(folder_day, DAY(created_at)),
    full_path = COALESCE(full_path, CONCAT('/', YEAR(created_at), '/', LPAD(MONTH(created_at), 2, '0'), '/', LPAD(DAY(created_at), 2, '0'), '/', COALESCE(name, CONCAT('package-', id)))),
    created_by_name = COALESCE(created_by_name, '系统迁移'),
    upload_status = COALESCE(upload_status, 'pending_upload'),
    is_deleted = COALESCE(is_deleted, FALSE);

-- asset_file fields required by AssetFile entity
CALL add_column_if_missing('asset_file', 'file_no', 'VARCHAR(40) NULL AFTER id');
CALL add_column_if_missing('asset_file', 'file_name', 'VARCHAR(255) NULL AFTER package_id');
CALL add_column_if_missing('asset_file', 'file_type', 'VARCHAR(20) NULL AFTER file_name');
CALL add_column_if_missing('asset_file', 'sort_order', 'INT NOT NULL DEFAULT 0 AFTER preview_url');
CALL add_column_if_missing('asset_file', 'upload_status', 'VARCHAR(40) NOT NULL DEFAULT ''success'' AFTER sort_order');
CALL add_column_if_missing('asset_file', 'created_by', 'BIGINT NULL AFTER upload_status');
CALL add_column_if_missing('asset_file', 'created_by_name', 'VARCHAR(80) NULL AFTER created_by');
CALL add_column_if_missing('asset_file', 'created_at', 'TIMESTAMP(6) NULL AFTER created_by_name');
CALL add_column_if_missing('asset_file', 'updated_at', 'TIMESTAMP(6) NULL AFTER created_at');
CALL add_column_if_missing('asset_file', 'is_deleted', 'BOOLEAN NOT NULL DEFAULT FALSE AFTER updated_at');
CALL add_column_if_missing('asset_file', 'deleted_at', 'TIMESTAMP(6) NULL AFTER is_deleted');
CALL add_column_if_missing('asset_file', 'deleted_by', 'BIGINT NULL AFTER deleted_at');
CALL add_column_if_missing('asset_file', 'purge_at', 'TIMESTAMP(6) NULL AFTER deleted_by');

UPDATE asset_file
SET file_no = COALESCE(file_no, CONCAT('FILE', id)),
    file_name = COALESCE(file_name, original_name, CONCAT('file-', id)),
    file_type = COALESCE(file_type, type, 'script'),
    sort_order = COALESCE(sort_order, 0),
    upload_status = COALESCE(upload_status, 'success'),
    created_by = COALESCE(created_by, uploaded_by, 1),
    created_by_name = COALESCE(created_by_name, '系统迁移'),
    created_at = COALESCE(created_at, uploaded_at, CURRENT_TIMESTAMP(6)),
    updated_at = COALESCE(updated_at, uploaded_at, created_at),
    is_deleted = COALESCE(is_deleted, FALSE);

ALTER TABLE asset_file
  MODIFY COLUMN file_name VARCHAR(255) NOT NULL,
  MODIFY COLUMN file_type VARCHAR(20) NOT NULL,
  MODIFY COLUMN created_by BIGINT NOT NULL,
  MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- lead_record fields required by Lead entity
CALL add_column_if_missing('lead_record', 'lead_no', 'VARCHAR(40) NULL AFTER id');
CALL add_column_if_missing('lead_record', 'source_type', 'VARCHAR(40) NULL AFTER lead_no');
CALL add_column_if_missing('lead_record', 'related_package_id', 'BIGINT NULL AFTER source_type');
CALL add_column_if_missing('lead_record', 'student_name', 'VARCHAR(120) NULL AFTER operator_id');
CALL add_column_if_missing('lead_record', 'wechat', 'VARCHAR(80) NULL AFTER phone');
CALL add_column_if_missing('lead_record', 'source_channel', 'VARCHAR(80) NULL AFTER wechat');
CALL add_column_if_missing('lead_record', 'target_country', 'VARCHAR(80) NULL AFTER source_channel');
CALL add_column_if_missing('lead_record', 'target_major', 'VARCHAR(120) NULL AFTER target_country');
CALL add_column_if_missing('lead_record', 'budget', 'VARCHAR(80) NULL AFTER target_major');
CALL add_column_if_missing('lead_record', 'degree_level', 'VARCHAR(80) NULL AFTER budget');
CALL add_column_if_missing('lead_record', 'assigned_to', 'BIGINT NULL AFTER status');
CALL add_column_if_missing('lead_record', 'assigned_to_name', 'VARCHAR(80) NULL AFTER assigned_to');

UPDATE lead_record
SET lead_no = COALESCE(lead_no, CONCAT('LD', id)),
    source_type = COALESCE(source_type, 'content_package'),
    related_package_id = COALESCE(related_package_id, package_id),
    student_name = COALESCE(student_name, customer_name, CONCAT('student-', id)),
    status = CASE status WHEN 'new_lead' THEN 'unassigned' WHEN 'contacted' THEN 'following' WHEN 'converted' THEN 'completed' ELSE status END;

ALTER TABLE lead_record
  MODIFY COLUMN student_name VARCHAR(120) NOT NULL,
  MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'unassigned';

-- ioas_task fields required by Task entity
CALL add_column_if_missing('ioas_task', 'task_type', 'VARCHAR(40) NULL AFTER id');
CALL add_column_if_missing('ioas_task', 'role_type', 'VARCHAR(20) NULL AFTER task_type');
CALL add_column_if_missing('ioas_task', 'related_package_id', 'BIGINT NULL AFTER role_type');
CALL add_column_if_missing('ioas_task', 'related_lead_id', 'BIGINT NULL AFTER related_package_id');
CALL add_column_if_missing('ioas_task', 'assignee_name', 'VARCHAR(80) NULL AFTER assignee_id');
CALL add_column_if_missing('ioas_task', 'progress', 'INT NOT NULL DEFAULT 0 AFTER status');
CALL add_column_if_missing('ioas_task', 'error_message', 'VARCHAR(1000) NULL AFTER progress');
CALL add_column_if_missing('ioas_task', 'completed_at', 'TIMESTAMP(6) NULL AFTER error_message');

UPDATE ioas_task
SET task_type = COALESCE(task_type, CASE WHEN type = 'media' THEN 'media_upload' ELSE 'operator_lead_generate' END),
    role_type = COALESCE(role_type, CASE WHEN type = 'media' THEN 'media' ELSE 'operator' END),
    related_package_id = COALESCE(related_package_id, package_id),
    related_lead_id = COALESCE(related_lead_id, lead_id),
    progress = COALESCE(progress, 0),
    status = CASE status WHEN 'in_progress' THEN 'processing' WHEN 'cancelled' THEN 'rejected' ELSE status END;

ALTER TABLE ioas_task
  MODIFY COLUMN task_type VARCHAR(40) NOT NULL,
  MODIFY COLUMN role_type VARCHAR(20) NOT NULL,
  MODIFY COLUMN status VARCHAR(40) NOT NULL DEFAULT 'pending';

-- indexes guarded for both fresh and partially migrated databases
CALL add_index_if_missing('content_package', 'idx_content_package_operator', 'operator_id');
CALL add_index_if_missing('content_package', 'idx_content_package_upload_status', 'upload_status');
CALL add_index_if_missing('content_package', 'idx_content_package_deleted', 'is_deleted');
CALL add_index_if_missing('asset_file', 'idx_asset_file_type', 'file_type');
CALL add_index_if_missing('asset_file', 'idx_asset_file_deleted', 'is_deleted');
CALL add_index_if_missing('lead_record', 'idx_lead_related_package', 'related_package_id');
CALL add_index_if_missing('lead_record', 'idx_lead_status', 'status');
CALL add_index_if_missing('ioas_task', 'idx_task_role_status', 'role_type, status');
CALL add_index_if_missing('ioas_task', 'idx_task_related_package', 'related_package_id');
CALL add_index_if_missing('ioas_task', 'idx_task_related_lead', 'related_lead_id');

DROP PROCEDURE add_column_if_missing;
DROP PROCEDURE add_index_if_missing;
