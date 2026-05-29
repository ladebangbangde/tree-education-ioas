INSERT INTO consultant_region (region_code, region_name, region_type, enabled, sort_order, remark, created_at, updated_at)
SELECT 'AU', '澳洲', 'REGION', 1, 10, '澳洲顾问服务区域', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM consultant_region WHERE region_code = 'AU');

INSERT INTO consultant_region (region_code, region_name, region_type, enabled, sort_order, remark, created_at, updated_at)
SELECT 'UK', '英国', 'REGION', 1, 20, '英国顾问服务区域', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM consultant_region WHERE region_code = 'UK');

INSERT INTO consultant_region (region_code, region_name, region_type, enabled, sort_order, remark, created_at, updated_at)
SELECT 'US', '美国', 'REGION', 1, 30, '美国顾问服务区域', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM consultant_region WHERE region_code = 'US');

UPDATE consultant_region SET enabled = 1, updated_at = CURRENT_TIMESTAMP WHERE region_code IN ('AU', 'UK', 'US');

INSERT INTO consultant_profile (user_id, consultant_name, phone, email, team_name, enabled, assign_enabled, max_daily_leads, current_daily_leads, created_at, updated_at)
SELECT u.id, 'Emily Carter', NULL, NULL, '澳洲顾问组', 1, 1, 30, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sys_user u
WHERE u.username = 'emily.carter'
  AND NOT EXISTS (SELECT 1 FROM consultant_profile p WHERE p.user_id = u.id);

INSERT INTO consultant_profile (user_id, consultant_name, phone, email, team_name, enabled, assign_enabled, max_daily_leads, current_daily_leads, created_at, updated_at)
SELECT u.id, 'Sophie Williams', NULL, NULL, '英国顾问组', 1, 1, 30, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sys_user u
WHERE u.username = 'sophie.williams'
  AND NOT EXISTS (SELECT 1 FROM consultant_profile p WHERE p.user_id = u.id);

INSERT INTO consultant_profile (user_id, consultant_name, phone, email, team_name, enabled, assign_enabled, max_daily_leads, current_daily_leads, created_at, updated_at)
SELECT u.id, 'Daniel Miller', NULL, NULL, '美国顾问组', 1, 1, 30, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sys_user u
WHERE u.username = 'daniel.miller'
  AND NOT EXISTS (SELECT 1 FROM consultant_profile p WHERE p.user_id = u.id);

UPDATE consultant_profile p JOIN sys_user u ON p.user_id = u.id
SET p.enabled = 1,
    p.assign_enabled = 1,
    p.consultant_name = u.display_name,
    p.updated_at = CURRENT_TIMESTAMP
WHERE u.username IN ('emily.carter', 'sophie.williams', 'daniel.miller');

INSERT INTO consultant_region_relation (consultant_id, region_id, enabled, priority, created_at, updated_at)
SELECT p.id, r.id, 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM consultant_profile p
JOIN sys_user u ON p.user_id = u.id
JOIN consultant_region r ON r.region_code = 'AU'
WHERE u.username = 'emily.carter'
  AND NOT EXISTS (SELECT 1 FROM consultant_region_relation rel WHERE rel.consultant_id = p.id AND rel.region_id = r.id);

INSERT INTO consultant_region_relation (consultant_id, region_id, enabled, priority, created_at, updated_at)
SELECT p.id, r.id, 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM consultant_profile p
JOIN sys_user u ON p.user_id = u.id
JOIN consultant_region r ON r.region_code = 'UK'
WHERE u.username = 'sophie.williams'
  AND NOT EXISTS (SELECT 1 FROM consultant_region_relation rel WHERE rel.consultant_id = p.id AND rel.region_id = r.id);

INSERT INTO consultant_region_relation (consultant_id, region_id, enabled, priority, created_at, updated_at)
SELECT p.id, r.id, 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM consultant_profile p
JOIN sys_user u ON p.user_id = u.id
JOIN consultant_region r ON r.region_code = 'US'
WHERE u.username = 'daniel.miller'
  AND NOT EXISTS (SELECT 1 FROM consultant_region_relation rel WHERE rel.consultant_id = p.id AND rel.region_id = r.id);

UPDATE consultant_region_relation rel
JOIN consultant_profile p ON rel.consultant_id = p.id
JOIN sys_user u ON p.user_id = u.id
SET rel.enabled = 1, rel.updated_at = CURRENT_TIMESTAMP
WHERE u.username IN ('emily.carter', 'sophie.williams', 'daniel.miller');
