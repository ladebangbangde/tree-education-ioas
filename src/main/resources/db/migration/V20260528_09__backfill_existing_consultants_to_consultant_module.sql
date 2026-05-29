DROP TEMPORARY TABLE IF EXISTS tmp_seq;
CREATE TEMPORARY TABLE tmp_seq (n INT PRIMARY KEY);
INSERT INTO tmp_seq (n) VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10);

DROP TEMPORARY TABLE IF EXISTS tmp_operator_regions;
CREATE TEMPORARY TABLE tmp_operator_regions AS
SELECT
    op.user_id,
    CASE UPPER(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(COALESCE(op.speciality_region_codes, ''), ',', s.n), ',', -1)))
        WHEN 'AUSTRALIA' THEN 'AU'
        WHEN '澳洲' THEN 'AU'
        WHEN 'UK' THEN 'UK'
        WHEN '英国' THEN 'UK'
        WHEN 'US' THEN 'US'
        WHEN 'USA' THEN 'US'
        WHEN '美国' THEN 'US'
        WHEN 'EUROPE' THEN 'EU'
        WHEN '欧洲' THEN 'EU'
        WHEN 'CANADA' THEN 'CA'
        WHEN '加拿大' THEN 'CA'
        WHEN 'SINGAPORE' THEN 'SG'
        WHEN '新加坡' THEN 'SG'
        WHEN 'JAPAN' THEN 'JP'
        WHEN '日本' THEN 'JP'
        WHEN 'HONGKONG' THEN 'HK'
        WHEN 'HONG_KONG' THEN 'HK'
        WHEN '中国香港' THEN 'HK'
        ELSE UPPER(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(COALESCE(op.speciality_region_codes, ''), ',', s.n), ',', -1)))
    END AS region_code,
    s.n * 10 AS priority
FROM operator_profile op
JOIN sys_user u ON u.id = op.user_id
JOIN tmp_seq s ON s.n <= 1 + LENGTH(COALESCE(op.speciality_region_codes, '')) - LENGTH(REPLACE(COALESCE(op.speciality_region_codes, ''), ',', ''))
WHERE u.role_code = 'CONSULTANT'
  AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(COALESCE(op.speciality_region_codes, ''), ',', s.n), ',', -1)) <> '';

DROP TEMPORARY TABLE IF EXISTS tmp_advisor_regions;
CREATE TEMPORARY TABLE tmp_advisor_regions AS
SELECT
    ap.user_id,
    CASE UPPER(TRIM(COALESCE(ap.responsible_region, '')))
        WHEN 'AUSTRALIA' THEN 'AU'
        WHEN '澳洲' THEN 'AU'
        WHEN 'UK' THEN 'UK'
        WHEN '英国' THEN 'UK'
        WHEN 'US' THEN 'US'
        WHEN 'USA' THEN 'US'
        WHEN '美国' THEN 'US'
        WHEN 'EUROPE' THEN 'EU'
        WHEN '欧洲' THEN 'EU'
        WHEN 'CANADA' THEN 'CA'
        WHEN '加拿大' THEN 'CA'
        WHEN 'SINGAPORE' THEN 'SG'
        WHEN '新加坡' THEN 'SG'
        WHEN 'JAPAN' THEN 'JP'
        WHEN '日本' THEN 'JP'
        WHEN 'HONGKONG' THEN 'HK'
        WHEN 'HONG_KONG' THEN 'HK'
        WHEN '中国香港' THEN 'HK'
        ELSE UPPER(TRIM(COALESCE(ap.responsible_region, '')))
    END AS region_code,
    10 AS priority
FROM advisor_profile ap
JOIN sys_user u ON u.id = ap.user_id
WHERE u.role_code = 'CONSULTANT'
  AND TRIM(COALESCE(ap.responsible_region, '')) <> '';

DROP TEMPORARY TABLE IF EXISTS tmp_assignment_regions;
CREATE TEMPORARY TABLE tmp_assignment_regions AS
SELECT
    ca.consultant_user_id AS user_id,
    CASE UPPER(TRIM(COALESCE(ca.region_code, '')))
        WHEN 'AUSTRALIA' THEN 'AU'
        WHEN '澳洲' THEN 'AU'
        WHEN 'UK' THEN 'UK'
        WHEN '英国' THEN 'UK'
        WHEN 'US' THEN 'US'
        WHEN 'USA' THEN 'US'
        WHEN '美国' THEN 'US'
        WHEN 'EUROPE' THEN 'EU'
        WHEN '欧洲' THEN 'EU'
        WHEN 'CANADA' THEN 'CA'
        WHEN '加拿大' THEN 'CA'
        WHEN 'SINGAPORE' THEN 'SG'
        WHEN '新加坡' THEN 'SG'
        WHEN 'JAPAN' THEN 'JP'
        WHEN '日本' THEN 'JP'
        WHEN 'HONGKONG' THEN 'HK'
        WHEN 'HONG_KONG' THEN 'HK'
        WHEN '中国香港' THEN 'HK'
        ELSE UPPER(TRIM(COALESCE(ca.region_code, '')))
    END AS region_code,
    ca.priority AS priority,
    ca.enabled AS enabled
FROM consultant_region_assignment ca
JOIN sys_user u ON u.id = ca.consultant_user_id
WHERE u.role_code = 'CONSULTANT'
  AND TRIM(COALESCE(ca.region_code, '')) <> '';

INSERT INTO consultant_region (region_code, region_name, region_type, enabled, sort_order, remark, created_at, updated_at)
SELECT DISTINCT x.region_code,
    CASE x.region_code
        WHEN 'AU' THEN '澳洲'
        WHEN 'UK' THEN '英国'
        WHEN 'US' THEN '美国'
        WHEN 'EU' THEN '欧洲'
        WHEN 'CA' THEN '加拿大'
        WHEN 'SG' THEN '新加坡'
        WHEN 'JP' THEN '日本'
        WHEN 'HK' THEN '中国香港'
        ELSE x.region_code
    END AS region_name,
    'REGION', 1, 999, '历史顾问数据兼容迁移自动补齐', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT region_code FROM tmp_operator_regions
    UNION
    SELECT region_code FROM tmp_advisor_regions
    UNION
    SELECT region_code FROM tmp_assignment_regions
) x
WHERE x.region_code IS NOT NULL AND x.region_code <> ''
  AND NOT EXISTS (SELECT 1 FROM consultant_region r WHERE r.region_code = x.region_code);

INSERT INTO consultant_profile (user_id, consultant_name, phone, email, team_name, avatar_url, public_title, public_bio, display_on_official, enabled, assign_enabled, max_daily_leads, current_daily_leads, sort_order, created_at, updated_at)
SELECT op.user_id,
       op.name,
       op.phone,
       NULL,
       IFNULL(op.team_name, '顾问团队'),
       op.consultant_avatar_public_url,
       op.public_title,
       op.public_bio,
       1,
       IFNULL(op.enabled, 1),
       1,
       30,
       0,
       0,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM operator_profile op
JOIN sys_user u ON u.id = op.user_id
WHERE u.role_code = 'CONSULTANT'
  AND NOT EXISTS (SELECT 1 FROM consultant_profile cp WHERE cp.user_id = op.user_id);

INSERT INTO consultant_profile (user_id, consultant_name, phone, email, team_name, avatar_url, public_title, public_bio, display_on_official, enabled, assign_enabled, max_daily_leads, current_daily_leads, sort_order, created_at, updated_at)
SELECT ap.user_id,
       ap.display_name,
       NULL,
       NULL,
       CONCAT(IFNULL(ap.responsible_region, '顾问'), '顾问组'),
       ap.avatar_url,
       ap.public_title,
       ap.bio,
       1,
       IFNULL(ap.enabled, 1),
       1,
       30,
       0,
       IFNULL(ap.sort_order, 0),
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM advisor_profile ap
JOIN sys_user u ON u.id = ap.user_id
WHERE u.role_code = 'CONSULTANT'
  AND NOT EXISTS (SELECT 1 FROM consultant_profile cp WHERE cp.user_id = ap.user_id);

INSERT INTO operator_profile (user_id, name, phone, team_name, enabled, consultant_avatar_public_url, public_title, public_bio, speciality_region_codes, speciality_region_names, created_at)
SELECT cp.user_id,
       cp.consultant_name,
       cp.phone,
       cp.team_name,
       IFNULL(cp.enabled, 1),
       cp.avatar_url,
       cp.public_title,
       cp.public_bio,
       NULL,
       NULL,
       CURRENT_TIMESTAMP
FROM consultant_profile cp
JOIN sys_user u ON u.id = cp.user_id
WHERE u.role_code = 'CONSULTANT'
  AND NOT EXISTS (SELECT 1 FROM operator_profile op WHERE op.user_id = cp.user_id);

INSERT INTO consultant_region_relation (consultant_id, region_id, enabled, priority, created_at, updated_at)
SELECT cp.id, r.id, IFNULL(x.enabled, 1), IFNULL(x.priority, 10), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT user_id, region_code, priority, 1 AS enabled FROM tmp_operator_regions
    UNION ALL
    SELECT user_id, region_code, priority, 1 AS enabled FROM tmp_advisor_regions
    UNION ALL
    SELECT user_id, region_code, priority, enabled FROM tmp_assignment_regions
) x
JOIN consultant_profile cp ON cp.user_id = x.user_id
JOIN consultant_region r ON r.region_code = x.region_code
WHERE x.region_code IS NOT NULL AND x.region_code <> ''
  AND NOT EXISTS (
      SELECT 1 FROM consultant_region_relation rel
      WHERE rel.consultant_id = cp.id AND rel.region_id = r.id
  );

UPDATE operator_profile op
JOIN consultant_profile cp ON cp.user_id = op.user_id
SET op.speciality_region_codes = (
        SELECT GROUP_CONCAT(r.region_code ORDER BY rel.priority SEPARATOR ',')
        FROM consultant_region_relation rel
        JOIN consultant_region r ON r.id = rel.region_id
        WHERE rel.consultant_id = cp.id AND rel.enabled = 1
    ),
    op.speciality_region_names = (
        SELECT GROUP_CONCAT(r.region_name ORDER BY rel.priority SEPARATOR ',')
        FROM consultant_region_relation rel
        JOIN consultant_region r ON r.id = rel.region_id
        WHERE rel.consultant_id = cp.id AND rel.enabled = 1
    )
WHERE op.speciality_region_codes IS NULL OR op.speciality_region_codes = '';
