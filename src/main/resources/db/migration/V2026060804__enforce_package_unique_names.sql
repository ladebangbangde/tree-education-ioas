set @schema_name = database();

update data_operation_topic_package p
join (
    select display_name
    from data_operation_topic_package
    where display_name is not null and display_name <> ''
    group by display_name
    having count(*) > 1
) d on d.display_name = p.display_name
set p.display_name = concat(p.display_name, '#', p.id),
    p.folder_name = concat(coalesce(p.folder_name, p.display_name), '#', p.id),
    p.updated_at = current_timestamp(6);

set @package_unique_index_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = @schema_name
      and table_name = 'data_operation_topic_package'
      and index_name = 'uk_data_operation_package_display_name'
);

set @add_package_unique_index_sql = if(
    @package_unique_index_count = 0,
    'alter table data_operation_topic_package add unique key uk_data_operation_package_display_name (display_name)',
    'select 1'
);
prepare add_package_unique_index_stmt from @add_package_unique_index_sql;
execute add_package_unique_index_stmt;
deallocate prepare add_package_unique_index_stmt;
