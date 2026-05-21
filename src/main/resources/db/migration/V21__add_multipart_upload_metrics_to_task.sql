DROP PROCEDURE IF EXISTS add_ioas_task_column_if_missing;

DELIMITER $$
CREATE PROCEDURE add_ioas_task_column_if_missing(
    IN p_column_name VARCHAR(128),
    IN p_column_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ioas_task'
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ioas_task ADD COLUMN ', p_column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_ioas_task_column_if_missing('upload_id', 'upload_id VARCHAR(300) NULL AFTER upload_object_key');
CALL add_ioas_task_column_if_missing('file_name', 'file_name VARCHAR(300) NULL AFTER upload_public_url');
CALL add_ioas_task_column_if_missing('file_size', 'file_size BIGINT NULL AFTER file_name');
CALL add_ioas_task_column_if_missing('uploaded_bytes', 'uploaded_bytes BIGINT NULL AFTER file_size');
CALL add_ioas_task_column_if_missing('speed_bytes_per_second', 'speed_bytes_per_second BIGINT NULL AFTER uploaded_bytes');
CALL add_ioas_task_column_if_missing('average_speed_bytes_per_second', 'average_speed_bytes_per_second BIGINT NULL AFTER speed_bytes_per_second');
CALL add_ioas_task_column_if_missing('part_count', 'part_count INT NULL AFTER average_speed_bytes_per_second');
CALL add_ioas_task_column_if_missing('completed_part_count', 'completed_part_count INT NULL AFTER part_count');
CALL add_ioas_task_column_if_missing('last_progress_at', 'last_progress_at DATETIME(6) NULL AFTER completed_part_count');

DROP PROCEDURE IF EXISTS add_ioas_task_column_if_missing;
