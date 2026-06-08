delete mv
from data_operation_metric_value mv
join data_operation_asset a on a.id = mv.asset_id
where a.asset_type = 'DATA_SCREENSHOT'
  and a.upload_status in ('failed', 'FAILED')
  and exists (
      select 1
      from data_operation_asset newer
      where newer.content_id = a.content_id
        and newer.asset_type = 'DATA_SCREENSHOT'
        and coalesce(newer.asset_group, '') = coalesce(a.asset_group, '')
        and newer.id > a.id
  );

delete old_asset
from data_operation_asset old_asset
where old_asset.asset_type = 'DATA_SCREENSHOT'
  and old_asset.upload_status in ('failed', 'FAILED')
  and exists (
      select 1
      from data_operation_asset newer_asset
      where newer_asset.content_id = old_asset.content_id
        and newer_asset.asset_type = 'DATA_SCREENSHOT'
        and coalesce(newer_asset.asset_group, '') = coalesce(old_asset.asset_group, '')
        and newer_asset.id > old_asset.id
  );

delete mv
from data_operation_metric_value mv
where mv.content_type = 'VIDEO'
  and mv.metric_key in ('copy_expand_rate', 'copy_finish_rate', 'comment_enter_rate', 'cover_click_rate');
