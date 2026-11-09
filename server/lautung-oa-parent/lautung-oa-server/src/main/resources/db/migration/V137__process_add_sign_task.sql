-- 加签任务表（ADR 0068，#288）：加签不注入 BPMN、不改已发布产物——加签任务与引擎任务
-- 配对表达「等待加签人」：sign_type BEFORE（前加签，原任务冻结、加签人全部通过后解挂回原
-- 处理人）/ AFTER（后加签，原处理人的通过已在发起时记账，加签人全部通过后才用该通过完成
-- 当前节点）。batch_no 是同一引擎任务内的批次序号：同一批次进行中不能再加签，前加签回到
-- 原处理人后可再来一批。status：PENDING 待处理 / APPROVED 已通过 / REJECTED 已拒绝 /
-- DESTROYED 已销毁（审批被拒绝或撤回等终态时未决加签任务的收场，不进任何人的待处理）。
--
-- 建表用 IF NOT EXISTS（与 V120 同款）；索引用 CREATE INDEX（V109 约定）。
-- idx_oa_process_add_sign_assignee 服务待处理第三源（按加签人查 PENDING 行）与销毁回写；
-- idx_oa_process_add_sign_task 服务挂起/批次判定（按引擎任务查未决批次）。两列组合均从
-- 高选择性等值列起步，新表数据量随审批动作增长，暂不另做 EXPLAIN 取证（#185 口径针对既有大表）。

CREATE TABLE IF NOT EXISTS oa_process_add_sign_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_id BIGINT NOT NULL,
  engine_task_id VARCHAR(64) NOT NULL,
  batch_no INT NOT NULL,
  sign_type VARCHAR(10) NOT NULL,
  assignee_id BIGINT NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
  update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX idx_oa_process_add_sign_assignee ON oa_process_add_sign_task (assignee_id, status);
CREATE INDEX idx_oa_process_add_sign_task ON oa_process_add_sign_task (engine_task_id, status);
