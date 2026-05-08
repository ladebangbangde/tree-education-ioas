ALTER TABLE asset_file
  ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN deleted_at TIMESTAMP(6) NULL,
  ADD COLUMN deleted_by BIGINT NULL,
  ADD COLUMN purge_at TIMESTAMP(6) NULL,
  ADD INDEX idx_asset_deleted_purge (is_deleted, purge_at);
