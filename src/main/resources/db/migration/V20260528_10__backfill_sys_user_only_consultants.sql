INSERT INTO consultant_profile (user_id, consultant_name, phone, email, team_name, avatar_url, public_title, public_bio, display_on_official, enabled, assign_enabled, max_daily_leads, current_daily_leads, sort_order, created_at, updated_at)
SELECT u.id,
       IFNULL(NULLIF(u.display_name, ''), u.username),
       NULL,
       NULL,
       IFNULL(NULLIF(u.department, ''), '顾问团队'),
       NULL,
       NULL,
       NULL,
       1,
       CASE WHEN u.status = 'ACTIVE' THEN 1 ELSE 0 END,
       1,
       30,
       0,
       0,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM sys_user u
WHERE u.role_code = 'CONSULTANT'
  AND NOT EXISTS (SELECT 1 FROM consultant_profile cp WHERE cp.user_id = u.id);

INSERT INTO operator_profile (user_id, name, phone, team_name, enabled, consultant_avatar_public_url, public_title, public_bio, speciality_region_codes, speciality_region_names, created_at)
SELECT u.id,
       IFNULL(NULLIF(u.display_name, ''), u.username),
       NULL,
       IFNULL(NULLIF(u.department, ''), '顾问团队'),
       CASE WHEN u.status = 'ACTIVE' THEN 1 ELSE 0 END,
       NULL,
       NULL,
       NULL,
       NULL,
       NULL,
       CURRENT_TIMESTAMP
FROM sys_user u
WHERE u.role_code = 'CONSULTANT'
  AND NOT EXISTS (SELECT 1 FROM operator_profile op WHERE op.user_id = u.id);
