DELETE op
FROM operator_profile op
LEFT JOIN sys_user u ON u.id = op.user_id
WHERE u.id IS NULL;

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
WHERE u.role_code = 'CONSULTANT'
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
        ELSE COALESCE(NULLIF(op.team_name, ''), '综合留学顾问团队')
    END
WHERE u.role_code = 'CONSULTANT'
  AND u.status = 'ACTIVE';
