CREATE TEMPORARY TABLE tmp_remove_seed_consultants AS
SELECT id FROM sys_user
WHERE username IN ('emily.carter', 'sophie.williams', 'daniel.miller');

DELETE rel
FROM consultant_region_relation rel
JOIN consultant_profile cp ON rel.consultant_id = cp.id
JOIN tmp_remove_seed_consultants t ON cp.user_id = t.id;

DELETE cp
FROM consultant_profile cp
JOIN tmp_remove_seed_consultants t ON cp.user_id = t.id;

DELETE ap
FROM advisor_profile ap
JOIN tmp_remove_seed_consultants t ON ap.user_id = t.id;

DELETE op
FROM operator_profile op
JOIN tmp_remove_seed_consultants t ON op.user_id = t.id;

DELETE u
FROM sys_user u
JOIN tmp_remove_seed_consultants t ON u.id = t.id;
