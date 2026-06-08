set @schema_name = database();

set @asset_group_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = @schema_name
      and table_name = 'data_operation_asset'
      and column_name = 'asset_group'
);
set @add_asset_group_sql = if(
    @asset_group_column_count = 0,
    'alter table data_operation_asset add column asset_group varchar(64) null after asset_type',
    'select 1'
);
prepare add_asset_group_stmt from @add_asset_group_sql;
execute add_asset_group_stmt;
deallocate prepare add_asset_group_stmt;

update data_operation_metric_definition
set enabled = 0, updated_at = current_timestamp(6)
where content_type = 'VIDEO'
  and metric_key in ('copy_expand_rate', 'copy_finish_rate', 'comment_enter_rate', 'cover_click_rate')
  and metric_label in ('文案展开率', '文案完读率', '评论进入率', '封面点击率');

delete from data_operation_metric_value
where content_type = 'VIDEO'
  and metric_key in ('copy_expand_rate', 'copy_finish_rate', 'comment_enter_rate', 'cover_click_rate');

update data_operation_asset a
join data_operation_content c on c.id = a.content_id
set a.asset_group = case
    when a.asset_group is not null and a.asset_group <> '' then a.asset_group
    when a.object_key like '%douyin_flow_analysis%' then 'DOUYIN_FLOW_ANALYSIS'
    when a.object_key like '%douyin_overview_chart%' then 'DOUYIN_OVERVIEW_CHART'
    else null
end
where a.asset_type = 'DATA_SCREENSHOT';
