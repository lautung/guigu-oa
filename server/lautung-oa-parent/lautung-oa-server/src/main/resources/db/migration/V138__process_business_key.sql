-- 业务键底座（ADR 0067，#289）：oa_process 用 (business_type, business_id) 两列指向业务单据，
-- 值域 <域>.<单据>（如 hr.transfer、attendance.leave、expense.claim）；通用审批两列 NULL。
-- 一张单据可积累多次审批（拒绝/撤回后重提起新审批），刻意不加唯一索引、不建关联表；
-- 单据表不存 process_id，详情按两列回查最近一条（读能力见 BusinessApprovalReader）。
-- 模板映射表 oa_business_template：business_type 主键 → process_template_id；映射行随各域票种子，
-- 本迁移只建表（本票不种业务映射行）。
ALTER TABLE oa_process ADD COLUMN business_type VARCHAR(64) DEFAULT NULL;
ALTER TABLE oa_process ADD COLUMN business_id BIGINT DEFAULT NULL;

-- 联合「普通」索引（#185 教训：建完用实发 SQL 验执行计划）。回查形状是
-- (business_type, business_id) 等值 + ORDER BY id DESC LIMIT 1，is_deleted 是残余谓词
-- （与 V108 的已发起索引同一取舍：等值列里插 is_deleted 会破坏 (类型,id 倒序) 的排序匹配）。
-- MySQL 8.4.13 EXPLAIN ANALYZE 取证（10 万通用行 + 600 业务行、键值命中 3 行含 1 软删）：
--   -> Limit: 1 row(s)  (cost=0.78 rows=0.3) (actual time=0.0426..0.0427 rows=1 loops=1)
--       -> Filter: (a.is_deleted = 0)  (cost=0.78 rows=0.3) (actual time=0.042..0.042 rows=1 loops=1)
--           -> Index lookup on a using idx_oa_process_business
--              (business_type='expense.claim', business_id=42) (reverse)
--              (cost=0.78 rows=3) (actual time=0.0207..0.0228 rows=2 loops=1)
-- 二级索引尾部隐含主键 id：等值前缀内反向扫描天然 id 倒序，无排序步骤；未命中键同计划 0.004ms。
CREATE INDEX idx_oa_process_business ON oa_process(business_type, business_id);

CREATE TABLE oa_business_template (
  business_type VARCHAR(64) NOT NULL,
  process_template_id BIGINT NOT NULL,
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (business_type)
);
