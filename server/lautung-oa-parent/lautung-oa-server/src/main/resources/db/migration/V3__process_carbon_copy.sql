-- 抄送规则不写入 BPMN；抄送行只授予审批可见性。
CREATE TABLE oa_process_cc_rule (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_definition_key VARCHAR(100) NOT NULL,
  task_definition_key VARCHAR(100) NOT NULL,
  user_id BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_oa_process_cc_rule UNIQUE (process_definition_key, task_definition_key, user_id)
);

CREATE TABLE oa_process_cc (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  source_key VARCHAR(100) NOT NULL,
  task_id VARCHAR(100) NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_oa_process_cc_source UNIQUE (source_type, source_key, user_id),
  CONSTRAINT uk_oa_process_cc_task UNIQUE (task_id, user_id)
);

CREATE INDEX idx_oa_process_cc_rule_task
  ON oa_process_cc_rule (process_definition_key, task_definition_key, is_deleted);
CREATE INDEX idx_oa_process_cc_user_process
  ON oa_process_cc (user_id, process_id, is_deleted);
