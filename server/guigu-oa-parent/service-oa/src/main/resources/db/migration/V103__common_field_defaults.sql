-- 公共字段的兜底默认值（ADR 0031）。
--
-- 应用侧由 CommonFieldsMetaObjectHandler 写入 create_time / update_time；这里的默认值只覆盖
-- 绕过实体的直接 SQL（含演示播种与人工修数），让漏写的插入不会留下空时间。默认值用
-- 会话时区，因此连接必须声明 time_zone=+08:00（见 application-dev.yml 的 sessionVariables、
-- compose.yaml 的 default-time-zone），否则兜底值与业务时间会差一个时区。
--
-- 列保持可空：历史行里无法证明创建时刻的保持 NULL，不回填、不编造（与 V4 的立场一致）。

ALTER TABLE sys_user ALTER COLUMN create_time SET DEFAULT (CURRENT_TIMESTAMP);
ALTER TABLE sys_user ALTER COLUMN update_time SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE sys_role ALTER COLUMN create_time SET DEFAULT (CURRENT_TIMESTAMP);
ALTER TABLE sys_role ALTER COLUMN update_time SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE sys_menu ALTER COLUMN create_time SET DEFAULT (CURRENT_TIMESTAMP);
ALTER TABLE sys_menu ALTER COLUMN update_time SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE sys_role_menu ALTER COLUMN create_time SET DEFAULT (CURRENT_TIMESTAMP);
ALTER TABLE sys_role_menu ALTER COLUMN update_time SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE sys_user_role ALTER COLUMN create_time SET DEFAULT (CURRENT_TIMESTAMP);
ALTER TABLE sys_user_role ALTER COLUMN update_time SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE oa_process_type ALTER COLUMN create_time SET DEFAULT (CURRENT_TIMESTAMP);
ALTER TABLE oa_process_type ALTER COLUMN update_time SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE oa_process_template ALTER COLUMN create_time SET DEFAULT (CURRENT_TIMESTAMP);
ALTER TABLE oa_process_template ALTER COLUMN update_time SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE oa_process ALTER COLUMN create_time SET DEFAULT (CURRENT_TIMESTAMP);
ALTER TABLE oa_process ALTER COLUMN update_time SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE oa_process_record ALTER COLUMN create_time SET DEFAULT (CURRENT_TIMESTAMP);
ALTER TABLE oa_process_record ALTER COLUMN update_time SET DEFAULT (CURRENT_TIMESTAMP);
