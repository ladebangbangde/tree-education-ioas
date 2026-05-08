-- First-phase backend hardening: audit login action and resilient defaults.
-- Code uses the existing tables; this migration only normalizes data created by previous phases.

UPDATE sys_user SET role_code = 'SUPER_ADMIN' WHERE username = 'admin' AND role_code = 'ADMIN';
UPDATE sys_user SET user_name = COALESCE(user_name, display_name), department = COALESCE(department, '管理部') WHERE username = 'admin';
UPDATE sys_user SET user_name = COALESCE(user_name, display_name), department = COALESCE(department, '媒体部') WHERE username = 'media';
UPDATE sys_user SET user_name = COALESCE(user_name, display_name), department = COALESCE(department, '运营部') WHERE username = 'operator';

UPDATE content_package
SET upload_status = CASE
  WHEN is_deleted = TRUE THEN 'deleted'
  WHEN script_count > 0 AND video_count > 0 AND image_count > 0 THEN 'completed'
  WHEN script_count + video_count + image_count > 0 THEN 'partial_completed'
  ELSE 'pending_upload'
END;

UPDATE lead_record SET status = 'unassigned' WHERE status = 'new_lead';
UPDATE lead_record SET source_type = COALESCE(source_type, 'content_package');
