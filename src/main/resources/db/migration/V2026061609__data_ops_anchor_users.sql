alter table data_operation_topic_package
    add column anchor_user_ids varchar(1000) null after media_names,
    add column anchor_names varchar(1000) null after anchor_user_ids;

insert into sys_user (username, password_hash, display_name, department, role_code, status, token_version, created_at)
values
    ('anchor1', '{noop}Tree@123456', '主播1', '主播部', 'ANCHOR', 'ACTIVE', 0, current_timestamp(6)),
    ('anchor2', '{noop}Tree@123456', '主播2', '主播部', 'ANCHOR', 'ACTIVE', 0, current_timestamp(6)),
    ('anchor3', '{noop}Tree@123456', '主播3', '主播部', 'ANCHOR', 'ACTIVE', 0, current_timestamp(6))
on duplicate key update
    display_name = values(display_name),
    department = values(department),
    role_code = values(role_code),
    status = values(status);
