-- Seed public consultant profile data used by OA and the official website.
-- Final region ownership:
-- Jora -> Europe, Christine -> UK, Irene -> Australia, Dango -> US.

DELIMITER $$

CREATE PROCEDURE add_consultant_avatar_column_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'operator_profile'
          AND COLUMN_NAME = 'consultant_avatar_public_url'
    ) THEN
        ALTER TABLE operator_profile ADD COLUMN consultant_avatar_public_url VARCHAR(1000) NULL AFTER consultant_qr_public_url;
    END IF;
END$$

DELIMITER ;

CALL add_consultant_avatar_column_if_missing();
DROP PROCEDURE add_consultant_avatar_column_if_missing;

UPDATE operator_profile p
JOIN sys_user u ON u.id = p.user_id
SET p.public_title = '欧洲留学规划顾问',
    p.public_bio = '负责欧洲方向，覆盖德国、法国、荷兰、爱尔兰等项目，擅长课程匹配和材料逻辑搭建。',
    p.speciality_region_codes = 'EUROPE',
    p.speciality_region_names = '欧洲',
    p.consultant_avatar_public_url = '/pics/consultant/Jora.png'
WHERE u.username = 'jora';

UPDATE operator_profile p
JOIN sys_user u ON u.id = p.user_id
SET p.public_title = '英国留学规划顾问',
    p.public_bio = '负责英国方向，深耕英国硕士申请，擅长商科、传媒、教育与跨专业方案设计。',
    p.speciality_region_codes = 'UK',
    p.speciality_region_names = '英国',
    p.consultant_avatar_public_url = '/pics/consultant/Christine.png'
WHERE u.username = 'christine';

UPDATE operator_profile p
JOIN sys_user u ON u.id = p.user_id
SET p.public_title = '澳洲留学规划顾问',
    p.public_bio = '负责澳洲方向，熟悉澳洲八大、商科、数据与工程申请路径，擅长制定清晰申请时间线。',
    p.speciality_region_codes = 'AUSTRALIA',
    p.speciality_region_names = '澳洲',
    p.consultant_avatar_public_url = '/pics/consultant/Ierene.png'
WHERE u.username = 'irene';

UPDATE operator_profile p
JOIN sys_user u ON u.id = p.user_id
SET p.public_title = '美国留学规划顾问',
    p.public_bio = '负责美国方向，熟悉研究生申请节奏，擅长选校梯度、背景梳理与长期规划。',
    p.speciality_region_codes = 'US',
    p.speciality_region_names = '美国',
    p.consultant_avatar_public_url = '/pics/consultant/Dango.png'
WHERE u.username = 'dango';

UPDATE consultant_region_assignment a
JOIN sys_user u ON u.id = a.consultant_user_id
SET a.region_code = 'EUROPE',
    a.region_name = '欧洲',
    a.priority = 10,
    a.enabled = TRUE
WHERE u.username = 'jora';

UPDATE consultant_region_assignment a
JOIN sys_user u ON u.id = a.consultant_user_id
SET a.region_code = 'UK',
    a.region_name = '英国',
    a.priority = 20,
    a.enabled = TRUE
WHERE u.username = 'christine';

UPDATE consultant_region_assignment a
JOIN sys_user u ON u.id = a.consultant_user_id
SET a.region_code = 'AUSTRALIA',
    a.region_name = '澳洲',
    a.priority = 30,
    a.enabled = TRUE
WHERE u.username = 'irene';

UPDATE consultant_region_assignment a
JOIN sys_user u ON u.id = a.consultant_user_id
SET a.region_code = 'US',
    a.region_name = '美国',
    a.priority = 40,
    a.enabled = TRUE
WHERE u.username = 'dango';
