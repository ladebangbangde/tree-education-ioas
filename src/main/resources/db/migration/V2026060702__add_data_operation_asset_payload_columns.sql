set @schema_name = database();

set @ocr_payload_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = @schema_name
      and table_name = 'data_operation_asset'
      and column_name = 'ocr_payload_json'
);

set @add_ocr_payload_sql = if(
    @ocr_payload_column_count = 0,
    'alter table data_operation_asset add column ocr_payload_json longtext null',
    'select 1'
);
prepare add_ocr_payload_stmt from @add_ocr_payload_sql;
execute add_ocr_payload_stmt;
deallocate prepare add_ocr_payload_stmt;

set @data_payload_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = @schema_name
      and table_name = 'data_operation_asset'
      and column_name = 'data_payload_json'
);

set @add_data_payload_sql = if(
    @data_payload_column_count = 0,
    'alter table data_operation_asset add column data_payload_json longtext null',
    'select 1'
);
prepare add_data_payload_stmt from @add_data_payload_sql;
execute add_data_payload_stmt;
deallocate prepare add_data_payload_stmt;
