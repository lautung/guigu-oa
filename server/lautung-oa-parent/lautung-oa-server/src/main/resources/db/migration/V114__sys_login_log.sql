-- 登录日志表（ADR 0054，#215）。
--
-- 与 sys_oper_log 同一套纪律：物理删除，没有 is_deleted / update_time，实体不继承 BaseEntity；
-- 只有 create_time 由 CommonFieldsMetaObjectHandler 按字段名填充（兜底默认值见下）。
-- username 存提交的原文（不做手机号打码，管理员排查要看到被撞的账号）；user_id / dept_id 是
-- 写入时按 username 解析的快照，查不到留空；browser / os 只存解析结果，UA 原串不落库。
--
-- status 四值：0 成功 / 1 失败 / 2 锁定 / 3 解锁。被锁定挡下的尝试不落库（ADR 0054）。
--
-- 建表用 IF NOT EXISTS，手工在新库/旧库重复执行不会炸；MySQL 8 不支持
-- CREATE INDEX IF NOT EXISTS，索引随 Flyway 的一次性执行创建（与 V112 的写法一致）。

CREATE TABLE IF NOT EXISTS sys_login_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) DEFAULT NULL,
  user_id BIGINT DEFAULT NULL,
  dept_id BIGINT DEFAULT NULL,
  ip VARCHAR(45) DEFAULT NULL,
  browser VARCHAR(50) DEFAULT NULL,
  os VARCHAR(50) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  msg VARCHAR(500) DEFAULT NULL,
  create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
  PRIMARY KEY (id)
);

CREATE INDEX idx_sys_login_log_create_time ON sys_login_log (create_time);
CREATE INDEX idx_sys_login_log_username_create_time ON sys_login_log (username, create_time);
CREATE INDEX idx_sys_login_log_user_id ON sys_login_log (user_id);
CREATE INDEX idx_sys_login_log_dept_id ON sys_login_log (dept_id);
