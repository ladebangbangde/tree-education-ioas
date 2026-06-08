set @schema_name = database();

set @content_type_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = @schema_name
      and table_name = 'data_operation_content'
      and column_name = 'content_type'
);

set @add_content_type_sql = if(
    @content_type_column_count = 0,
    'alter table data_operation_content add column content_type varchar(32) null default ''IMAGE_TEXT'' after platform_code',
    'select 1'
);
prepare add_content_type_stmt from @add_content_type_sql;
execute add_content_type_stmt;
deallocate prepare add_content_type_stmt;

update data_operation_content
set content_type = case
    when content_summary like '%视频%' then 'VIDEO'
    when content_summary like '%图文%' then 'IMAGE_TEXT'
    when content_title like '%视频%' then 'VIDEO'
    else coalesce(content_type, 'IMAGE_TEXT')
end
where content_type is null or content_type = '';
