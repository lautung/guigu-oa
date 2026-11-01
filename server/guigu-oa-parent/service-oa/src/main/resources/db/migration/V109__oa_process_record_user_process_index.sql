-- 已处理列表按审批去重改由账本动作记录供给（#186，ADR 0049）：count 与行查询都是
-- 「按处理人过滤 + 按审批分组（DISTINCT/GROUP BY process_id）+ 取最近一次处理时间」。
-- 列序 (operate_user_id, process_id)：等值过滤与分组键都走索引，InnoDB 二级索引隐式以
-- 主键 id 结尾；max(create_time) 需读行（单用户动作记录量小，可接受），外层排序
-- （最近处理时间倒序）仍对该用户的去重结果排序。不含 is_deleted / description：两者
-- 无等值条件，放进来破坏分组匹配；record 行实际不软删、发起记录按文案排除。
-- 写法用 CREATE INDEX（V108 同款）：H2 合同测试跑真迁移，MySQL 专有的
-- ALTER TABLE ... ADD INDEX 语法会在 H2 上炸（本轮首次全量已踩）。
-- 建后必须用实发 SQL 验证执行计划（#185 教训：索引建了 ≠ 优化器会用）。
CREATE INDEX idx_oa_process_record_user_process ON oa_process_record(operate_user_id, process_id);
