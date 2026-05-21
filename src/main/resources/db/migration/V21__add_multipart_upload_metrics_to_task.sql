ALTER TABLE ioas_task
    ADD COLUMN upload_id VARCHAR(300) NULL AFTER upload_object_key,
    ADD COLUMN file_name VARCHAR(300) NULL AFTER upload_public_url,
    ADD COLUMN file_size BIGINT NULL AFTER file_name,
    ADD COLUMN uploaded_bytes BIGINT NULL AFTER file_size,
    ADD COLUMN speed_bytes_per_second BIGINT NULL AFTER uploaded_bytes,
    ADD COLUMN average_speed_bytes_per_second BIGINT NULL AFTER speed_bytes_per_second,
    ADD COLUMN part_count INT NULL AFTER average_speed_bytes_per_second,
    ADD COLUMN completed_part_count INT NULL AFTER part_count,
    ADD COLUMN last_progress_at DATETIME(6) NULL AFTER completed_part_count;
