ALTER TABLE consultant_profile ADD COLUMN avatar_url VARCHAR(500) NULL;
ALTER TABLE consultant_profile ADD COLUMN public_title VARCHAR(120) NULL;
ALTER TABLE consultant_profile ADD COLUMN public_bio VARCHAR(1000) NULL;
ALTER TABLE consultant_profile ADD COLUMN display_on_official TINYINT(1) NOT NULL DEFAULT 1;
ALTER TABLE consultant_profile ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE consultant_profile
SET public_title = CONCAT(IFNULL(team_name, '顾问'), '规划顾问')
WHERE public_title IS NULL;

UPDATE consultant_profile
SET public_bio = '资深留学规划顾问，擅长结合学生背景制定清晰可执行的申请方案。'
WHERE public_bio IS NULL;
