-- Repair historical official website leads that were assigned to operators or invalid users.
-- Official website leads must be owned by CONSULTANT users only.

UPDATE lead_record l
LEFT JOIN sys_user assigned_user ON assigned_user.id = l.assigned_to
JOIN sys_user target_user ON target_user.username = CASE
    WHEN l.intention_region_code IN ('UK', 'AUSTRALIA') OR l.intention_region_name IN ('英国', '澳洲') OR l.target_country IN ('英国', '澳洲', '澳大利亚', '新西兰') THEN 'consultant01'
    WHEN l.intention_region_code = 'EUROPE' OR l.intention_region_name = '欧洲' OR l.target_country IN ('欧洲', '法国', '德国', '荷兰', '瑞士', '爱尔兰') THEN 'consultant02'
    WHEN l.intention_region_code = 'US' OR l.intention_region_name = '美国' OR l.target_country IN ('美国', '加拿大') THEN 'consultant03'
    ELSE 'consultant01'
END
LEFT JOIN operator_profile target_profile ON target_profile.user_id = target_user.id
SET l.assigned_to = target_user.id,
    l.assigned_to_name = target_user.display_name,
    l.operator_id = target_profile.id,
    l.status = 'assigned',
    l.assign_mode = 'repair_consultant_assignment',
    l.assign_reason = CONCAT('历史官网线索修复：原分配账号[', COALESCE(assigned_user.username, '不存在账号'), ']不是有效顾问，已按意向区域改分配给顾问[', target_user.display_name, ']'),
    l.notify_status = 'repair_notified_pending',
    l.assigned_at = COALESCE(l.assigned_at, NOW()),
    l.updated_at = NOW()
WHERE l.source_type = 'official_website'
  AND (
      l.assigned_to IS NULL
      OR assigned_user.id IS NULL
      OR assigned_user.role_code <> 'CONSULTANT'
      OR assigned_user.status <> 'ACTIVE'
  )
  AND target_user.role_code = 'CONSULTANT'
  AND target_user.status = 'ACTIVE';
