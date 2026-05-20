ALTER TABLE ioas_task
    ADD COLUMN upload_bucket_name VARCHAR(100) NULL AFTER error_message,
    ADD COLUMN upload_object_key VARCHAR(500) NULL AFTER upload_bucket_name,
    ADD COLUMN upload_public_url VARCHAR(1000) NULL AFTER upload_object_key;
