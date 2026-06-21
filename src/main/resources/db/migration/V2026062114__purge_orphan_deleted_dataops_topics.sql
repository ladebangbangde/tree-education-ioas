-- Purge deleted/orphan DataOps package descendants so deleted packages cannot leave visible sub-topics.
-- This is intentionally conservative and targets only records whose parent package/topic/content is missing
-- or explicitly marked as deleted/removed/archived.

DELETE mv
FROM data_operation_metric_value mv
LEFT JOIN data_operation_content c ON c.id = mv.content_id
LEFT JOIN data_operation_platform_topic t ON t.id = mv.platform_topic_id
LEFT JOIN data_operation_topic_package p ON p.id = mv.topic_package_id
WHERE (mv.topic_package_id IS NOT NULL AND p.id IS NULL)
   OR (p.id IS NOT NULL AND LOWER(COALESCE(p.status, '')) IN ('deleted', 'removed', 'archived'))
   OR (mv.platform_topic_id IS NOT NULL AND t.id IS NULL)
   OR (t.id IS NOT NULL AND LOWER(COALESCE(t.status, '')) IN ('deleted', 'removed', 'archived'))
   OR (mv.content_id IS NOT NULL AND c.id IS NULL)
   OR (c.id IS NOT NULL AND LOWER(COALESCE(c.status, '')) IN ('deleted', 'removed', 'archived'));

DELETE a
FROM data_operation_asset a
LEFT JOIN data_operation_topic_package p ON p.id = a.package_id
LEFT JOIN data_operation_platform_topic t ON t.id = a.platform_topic_id
LEFT JOIN data_operation_content c ON c.id = a.content_id
WHERE (a.package_id IS NOT NULL AND p.id IS NULL)
   OR (p.id IS NOT NULL AND LOWER(COALESCE(p.status, '')) IN ('deleted', 'removed', 'archived'))
   OR (a.platform_topic_id IS NOT NULL AND t.id IS NULL)
   OR (t.id IS NOT NULL AND LOWER(COALESCE(t.status, '')) IN ('deleted', 'removed', 'archived'))
   OR (a.content_id IS NOT NULL AND c.id IS NULL)
   OR (c.id IS NOT NULL AND LOWER(COALESCE(c.status, '')) IN ('deleted', 'removed', 'archived'));

DELETE c
FROM data_operation_content c
LEFT JOIN data_operation_topic_package p ON p.id = c.package_id
LEFT JOIN data_operation_platform_topic t ON t.id = c.platform_topic_id
WHERE (c.package_id IS NOT NULL AND p.id IS NULL)
   OR (p.id IS NOT NULL AND LOWER(COALESCE(p.status, '')) IN ('deleted', 'removed', 'archived'))
   OR (c.platform_topic_id IS NOT NULL AND t.id IS NULL)
   OR (t.id IS NOT NULL AND LOWER(COALESCE(t.status, '')) IN ('deleted', 'removed', 'archived'))
   OR LOWER(COALESCE(c.status, '')) IN ('deleted', 'removed', 'archived');

DELETE t
FROM data_operation_platform_topic t
LEFT JOIN data_operation_topic_package p ON p.id = t.package_id
WHERE (t.package_id IS NOT NULL AND p.id IS NULL)
   OR (p.id IS NOT NULL AND LOWER(COALESCE(p.status, '')) IN ('deleted', 'removed', 'archived'))
   OR LOWER(COALESCE(t.status, '')) IN ('deleted', 'removed', 'archived');
