-- Standard OA test accounts
-- Password for all accounts: 123456
-- Roles: 1 super admin, 3 media users, 3 operator users, 3 consultant users.

INSERT INTO sys_user (username, password_hash, display_name, department, role_code, status, created_at)
VALUES
    ('admin', '{noop}123456', '系统超管', '管理中心', 'SUPER_ADMIN', 'ACTIVE', NOW()),
    ('media01', '{noop}123456', '媒体老师01', '媒体内容组', 'MEDIA', 'ACTIVE', NOW()),
    ('media02', '{noop}123456', '媒体老师02', '媒体内容组', 'MEDIA', 'ACTIVE', NOW()),
    ('media03', '{noop}123456', '媒体老师03', '媒体内容组', 'MEDIA', 'ACTIVE', NOW()),
    ('operator01', '{noop}123456', '运营老师01', '运营增长组', 'OPERATOR', 'ACTIVE', NOW()),
    ('operator02', '{noop}123456', '运营老师02', '运营增长组', 'OPERATOR', 'ACTIVE', NOW()),
    ('operator03', '{noop}123456', '运营老师03', '运营增长组', 'OPERATOR', 'ACTIVE', NOW()),
    ('consultant01', '{noop}123456', '澳洲英国顾问', '顾问团队', 'CONSULTANT', 'ACTIVE', NOW()),
    ('consultant02', '{noop}123456', '欧洲顾问', '顾问团队', 'CONSULTANT', 'ACTIVE', NOW()),
    ('consultant03', '{noop}123456', '美国顾问', '顾问团队', 'CONSULTANT', 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    display_name = VALUES(display_name),
    department = VALUES(department),
    role_code = VALUES(role_code),
    status = VALUES(status);

-- Backward-compatible normalization for earlier seed accounts.
-- Keep existing data rows, but make the standard test accounts above the official ones.
UPDATE sys_user
SET status = 'ACTIVE'
WHERE username IN ('admin', 'media01', 'media02', 'media03', 'operator01', 'operator02', 'operator03', 'consultant01', 'consultant02', 'consultant03');

-- Remove invalid assignment profiles that point to deleted or non-existing users.
DELETE op
FROM operator_profile op
LEFT JOIN sys_user u ON u.id = op.user_id
WHERE u.id IS NULL;

-- The current assignment pool is used by official website lead routing.
-- It should contain consultant users, not operator users.
INSERT INTO operator_profile (user_id, name, phone, team_name, enabled, created_at)
SELECT u.id,
       u.display_name,
       '',
       CASE u.username
           WHEN 'consultant01' THEN '澳洲 英国 留学顾问团队'
           WHEN 'consultant02' THEN '欧洲 法国 德国 荷兰 瑞士 爱尔兰 留学顾问团队'
           WHEN 'consultant03' THEN '美国 加拿大 留学顾问团队'
           ELSE '综合留学顾问团队'
       END,
       1,
       NOW()
FROM sys_user u
WHERE u.username IN ('consultant01', 'consultant02', 'consultant03')
  AND u.role_code = 'CONSULTANT'
  AND u.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM operator_profile op WHERE op.user_id = u.id
  );

UPDATE operator_profile op
JOIN sys_user u ON u.id = op.user_id
SET op.name = u.display_name,
    op.enabled = 1,
    op.team_name = CASE u.username
        WHEN 'consultant01' THEN '澳洲 英国 留学顾问团队'
        WHEN 'consultant02' THEN '欧洲 法国 德国 荷兰 瑞士 爱尔兰 留学顾问团队'
        WHEN 'consultant03' THEN '美国 加拿大 留学顾问团队'
        ELSE op.team_name
    END
WHERE u.username IN ('consultant01', 'consultant02', 'consultant03');

-- Disable operator_profile records bound to non-consultant users to avoid official website leads
-- being assigned to operators instead of consultants.
UPDATE operator_profile op
JOIN sys_user u ON u.id = op.user_id
SET op.enabled = 0
WHERE u.role_code <> 'CONSULTANT';
