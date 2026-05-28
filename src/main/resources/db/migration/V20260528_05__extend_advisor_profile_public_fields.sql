ALTER TABLE advisor_profile ADD COLUMN region_code VARCHAR(40) NULL;
ALTER TABLE advisor_profile ADD COLUMN public_title VARCHAR(120) NULL;
ALTER TABLE advisor_profile ADD COLUMN avatar_url VARCHAR(500) NULL;

UPDATE advisor_profile
SET region_code = CASE responsible_region
    WHEN '澳洲' THEN 'AU'
    WHEN '美国' THEN 'US'
    WHEN '英国' THEN 'UK'
    WHEN '欧洲' THEN 'EU'
    ELSE responsible_region
END
WHERE region_code IS NULL;

UPDATE advisor_profile
SET public_title = CONCAT(responsible_region, '规划顾问')
WHERE public_title IS NULL;
