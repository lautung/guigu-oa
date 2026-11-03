-- 操作日志表（ADR 0054，#213）。
--
-- 审计是历史事实：物理删除，没有 is_deleted / update_time，实体也不继承 BaseEntity；
-- 只有 create_time 由 CommonFieldsMetaObjectHandler 按字段名填充（兜底默认值见下）。
-- title / oper_name / dept_name 都是写入时快照，读侧不 join sys_menu / sys_user。
--
-- business_type 存稳定小整数（不是枚举序号，顺序变化不改码），映射：
--   0 其他 / 1 新增 / 2 修改 / 3 删除 / 4 授权 / 5 发布 / 6 清空 / 7 解锁
-- 新增类型必须同时改 BusinessType 与这条注释。
--
-- 列宽按存储意图：oper_param / error_msg 用 VARCHAR(2000) 而不是 TEXT，是故意让库当第二道闸
-- ——应用先截断 2000，库再挡一次，bug 不会静默写进 1MB。
--
-- 建表用 IF NOT EXISTS，手工在新库/旧库重复执行不会炸；MySQL 8 不支持
-- CREATE INDEX IF NOT EXISTS，索引随 Flyway 的一次性执行创建（与 V1 的写法一致）。

CREATE TABLE IF NOT EXISTS sys_oper_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(100) DEFAULT NULL,
  business_type TINYINT DEFAULT NULL,
  method VARCHAR(200) DEFAULT NULL,
  request_method VARCHAR(10) DEFAULT NULL,
  oper_id BIGINT DEFAULT NULL,
  oper_name VARCHAR(50) DEFAULT NULL,
  dept_id BIGINT DEFAULT NULL,
  dept_name VARCHAR(50) DEFAULT NULL,
  oper_url VARCHAR(255) DEFAULT NULL,
  oper_ip VARCHAR(64) DEFAULT NULL,
  oper_param VARCHAR(2000) DEFAULT NULL,
  result_code INT DEFAULT NULL,
  result_message VARCHAR(500) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  error_msg VARCHAR(2000) DEFAULT NULL,
  cost_time BIGINT DEFAULT NULL,
  create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
  PRIMARY KEY (id)
);

CREATE INDEX idx_sys_oper_log_create_time ON sys_oper_log (create_time);
CREATE INDEX idx_sys_oper_log_oper_id_create_time ON sys_oper_log (oper_id, create_time);
CREATE INDEX idx_sys_oper_log_dept_id ON sys_oper_log (dept_id);
