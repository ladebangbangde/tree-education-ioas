CREATE TABLE IF NOT EXISTS consultant_region_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    consultant_profile_id BIGINT NOT NULL,
    consultant_user_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    region_code VARCHAR(40) NOT NULL,
    region_name VARCHAR(80) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_consultant_region (consultant_user_id, region_code),
    KEY idx_consultant_region_code (region_code, enabled),
    KEY idx_consultant_user (consultant_user_id, enabled)
);

INSERT INTO consultant_region_assignment
(consultant_profile_id, consultant_user_id, region_id, region_code, region_name, priority, enabled, created_at)
SELECT op.id, u.id, r.id, r.code, r.name,
       CASE
           WHEN u.username = 'consultant01' AND r.code IN ('UK', 'AUSTRALIA') THEN 10
           WHEN u.username = 'consultant02' AND r.code = 'EUROPE' THEN 10
           WHEN u.username = 'consultant03' AND r.code = 'US' THEN 10
           WHEN r.code = 'OTHER' THEN 90
           ELSE 100
       END,
       1,
       NOW()
FROM sys_user u
JOIN operator_profile op ON op.user_id = u.id
JOIN intention_region r ON (
       (u.username = 'consultant01' AND r.code IN ('UK', 'AUSTRALIA', 'OTHER'))
    OR (u.username = 'consultant02' AND r.code IN ('EUROPE', 'OTHER'))
    OR (u.username = 'consultant03' AND r.code IN ('US', 'OTHER'))
)
WHERE u.role_code = 'CONSULTANT'
  AND u.status = 'ACTIVE'
  AND op.enabled = 1
  AND r.enabled = 1
ON DUPLICATE KEY UPDATE
    consultant_profile_id = VALUES(consultant_profile_id),
    region_id = VALUES(region_id),
    region_name = VALUES(region_name),
    priority = VALUES(priority),
    enabled = VALUES(enabled);
