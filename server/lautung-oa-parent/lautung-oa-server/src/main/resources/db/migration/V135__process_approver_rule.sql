-- 审批人规则子表（ADR 0065/0069，#285）：按 模板 + BPMN userTask id 存每节点的
-- 单选审批人来源（无规则行的节点沿用 BPMN assignee）。未发布可改；发布时随
-- 产物冻结成 oa-approvers.json sidecar，此后不再读写这张表决定已发布模板的审批人。
-- 唯一索引钉「每节点单选」；规则替换用整体重写（硬删后插入），已发布模板不可再改。
CREATE TABLE oa_process_approver_rule (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_template_id BIGINT DEFAULT NULL,
  task_definition_key VARCHAR(100) DEFAULT NULL,
  source_type VARCHAR(20) DEFAULT NULL,
  target_id BIGINT DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_approver_rule_node ON oa_process_approver_rule(process_template_id, task_definition_key);
