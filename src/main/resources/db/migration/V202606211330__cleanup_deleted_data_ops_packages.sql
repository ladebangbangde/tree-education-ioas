-- Clean up historical soft-deleted DataOps packages and descendants.
-- Goal: once a package/topic/content is deleted, it must not appear in UI, assets, metrics, or Excel reports.

-- Metrics attached to deleted packages/topics/contents/assets.
DELETE mv
FROM data_operation_metric_value mv
JOIN data_operation_topic_package p ON p.id = mv.topic_package_id
WHERE LOWER(COALESCE(p.status, '')) IN ('deleted', 'removed', 'archived');

DELETE mv
FROM data_operation_metric_value mv
JOIN data_operation_platform_topic t ON t.id = mv.platform_topic_id
WHERE LOWER(COALESCE(t.status, '')) IN ('deleted', 'removed', 'archived');

DELETE mv
FROM data_operation_metric_value mv
JOIN data_operation_content c ON c.id = mv.content_id
WHERE LOWER(COALESCE(c.status, '')) IN ('deleted', 'removed', 'archived');

DELETE mv
FROM data_operation_metric_value mv
JOIN data_operation_asset a ON a.id = mv.asset_id
JOIN data_operation_topic_package p ON p.id = a.package_id
WHERE LOWER(COALESCE(p.status, '')) IN ('deleted', 'removed', 'archived');

-- Assets under deleted packages/topics/contents.
DELETE a
FROM data_operation_asset a
JOIN data_operation_topic_package p ON p.id = a.package_id
WHERE LOWER(COALESCE(p.status, '')) IN ('deleted', 'removed', 'archived');

DELETE a
FROM data_operation_asset a
JOIN data_operation_platform_topic t ON t.id = a.platform_topic_id
WHERE LOWER(COALESCE(t.status, '')) IN ('deleted', 'removed', 'archived');

DELETE a
FROM data_operation_asset a
JOIN data_operation_content c ON c.id = a.content_id
WHERE LOWER(COALESCE(c.status, '')) IN ('deleted', 'removed', 'archived');

-- Contents and topics under deleted packages.
DELETE c
FROM data_operation_content c
JOIN data_operation_topic_package p ON p.id = c.package_id
WHERE LOWER(COALESCE(p.status, '')) IN ('deleted', 'removed', 'archived')
   OR LOWER(COALESCE(c.status, '')) IN ('deleted', 'removed', 'archived');

DELETE t
FROM data_operation_platform_topic t
JOIN data_operation_topic_package p ON p.id = t.package_id
WHERE LOWER(COALESCE(p.status, '')) IN ('deleted', 'removed', 'archived')
   OR LOWER(COALESCE(t.status, '')) IN ('deleted', 'removed', 'archived');

-- Finally remove the deleted package rows themselves so reports cannot join them.
DELETE FROM data_operation_topic_package
WHERE LOWER(COALESCE(status, '')) IN ('deleted', 'removed', 'archived');
