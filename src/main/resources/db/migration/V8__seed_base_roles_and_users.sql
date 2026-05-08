INSERT INTO sys_role(code, name) VALUES ('ADMIN','管理员'),('MEDIA','媒体'),('OPERATOR','运营');
INSERT INTO sys_permission(code, name) VALUES ('media:write','媒体写入'),('media:read','媒体读取'),('lead:write','线索写入'),('report:read','报表读取');
INSERT INTO sys_user(username,password_hash,display_name,role_code,status) VALUES
('admin','{noop}Admin@123456','系统管理员','ADMIN','ACTIVE'),
('media','{noop}Media@123456','媒体同学','MEDIA','ACTIVE'),
('operator','{noop}Operator@123456','运营同学','OPERATOR','ACTIVE');
INSERT INTO operator_profile(user_id,name,phone,team_name,enabled) VALUES (3,'运营同学','13800000000','默认运营组',TRUE);
