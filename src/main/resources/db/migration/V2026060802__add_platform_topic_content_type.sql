set @schema_name = database();

set @topic_content_type_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = @schema_name
      and table_name = 'data_operation_platform_topic'
      and column_name = 'content_type'
);

set @add_topic_content_type_sql = if(
    @topic_content_type_column_count = 0,
    'alter table data_operation_platform_topic add column content_type varchar(32) null default ''IMAGE_TEXT'' after platform_name',
    'select 1'
);
prepare add_topic_content_type_stmt from @add_topic_content_type_sql;
execute add_topic_content_type_stmt;
deallocate prepare add_topic_content_type_stmt;

update data_operation_platform_topic t
set content_type = coalesce(
    (
        select c.content_type
        from data_operation_content c
        where c.platform_topic_id = t.id
          and c.content_type is not null
          and c.content_type <> ''
        order by c.id desc
        limit 1
    ),
    t.content_type,
    'IMAGE_TEXT'
)
where t.content_type is null or t.content_type = '';
