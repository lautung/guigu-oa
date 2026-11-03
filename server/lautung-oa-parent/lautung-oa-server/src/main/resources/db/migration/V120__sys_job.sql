-- 定时任务表与执行记录表（ADR 0060，#238）。
--
-- sys_job 的任务行由启动登记产生（扫描 MonitorJob bean，缺行插入、有行不覆盖），
-- 页面不提供新增与删除（删掉的行下次启动会被补回），只能改 cron / 启停 / 并发 / 备注。
-- sys_job_log 是执行事实：无公共字段、无 is_deleted（保留策略在写入路径做物理删除，
-- 每任务留最近 20 条、全局留最近 500 条），毫秒精度落在 duration_ms，started_at/finished_at
-- 与仓库其余表一致用秒精度 DATETIME。
--
-- 建表用 IF NOT EXISTS，手工在新库/旧库重复执行不会炸；MySQL 8 不支持
-- CREATE INDEX IF NOT EXISTS，索引随 Flyway 的一次性执行创建（与 V112/V114 的写法一致）。

CREATE TABLE IF NOT EXISTS sys_job (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(50) DEFAULT NULL,
  bean_name VARCHAR(100) NOT NULL,
  cron VARCHAR(50) DEFAULT NULL,
  enabled TINYINT NOT NULL DEFAULT 0,
  concurrency TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
  update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_sys_job_bean_name ON sys_job (bean_name);

CREATE TABLE IF NOT EXISTS sys_job_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_id BIGINT DEFAULT NULL,
  job_name VARCHAR(50) DEFAULT NULL,
  trigger_type TINYINT DEFAULT NULL,
  status TINYINT DEFAULT NULL,
  message VARCHAR(1000) DEFAULT NULL,
  started_at DATETIME DEFAULT NULL,
  finished_at DATETIME DEFAULT NULL,
  duration_ms BIGINT DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_sys_job_log_job_started ON sys_job_log (job_id, started_at);
CREATE INDEX idx_sys_job_log_started ON sys_job_log (started_at);
