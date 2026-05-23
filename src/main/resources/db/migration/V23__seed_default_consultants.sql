INSERT IGNORE INTO sys_user(username, password_hash, display_name, department, role_code, status, created_at) VALUES
('consultant01', '{noop}Tree@2026!01', '澳洲英国顾问', '顾问部', 'CONSULTANT', 'ACTIVE', CURRENT_TIMESTAMP(6)),
('consultant02', '{noop}Tree@2026!02', '欧洲顾问', '顾问部', 'CONSULTANT', 'ACTIVE', CURRENT_TIMESTAMP(6)),
('consultant03', '{noop}Tree@2026!03', '美国顾问', '顾问部', 'CONSULTANT', 'ACTIVE', CURRENT_TIMESTAMP(6));

INSERT IGNORE INTO consultant_profile(user_id, consultant_name, phone, email, team_name, enabled, assign_enabled, max_daily_leads, current_daily_leads)
SELECT id, display_name, NULL, NULL, '顾问部', b'1', b'1', 30, 0
FROM sys_user
WHERE username IN ('consultant01','consultant02','consultant03');

INSERT IGNORE INTO consultant_region_relation(consultant_id, region_id, enabled, priority)
SELECT c.id, r.id, b'1', 10
FROM consultant_profile c
JOIN sys_user u ON u.id = c.user_id
JOIN consultant_region r ON r.region_code IN ('AUSTRALIA','UK')
WHERE u.username = 'consultant01';

INSERT IGNORE INTO consultant_region_relation(consultant_id, region_id, enabled, priority)
SELECT c.id, r.id, b'1', 10
FROM consultant_profile c
JOIN sys_user u ON u.id = c.user_id
JOIN consultant_region r ON r.region_code = 'EUROPE'
WHERE u.username = 'consultant02';

INSERT IGNORE INTO consultant_region_relation(consultant_id, region_id, enabled, priority)
SELECT c.id, r.id, b'1', 10
FROM consultant_profile c
JOIN sys_user u ON u.id = c.user_id
JOIN consultant_region r ON r.region_code = 'USA'
WHERE u.username = 'consultant03';
