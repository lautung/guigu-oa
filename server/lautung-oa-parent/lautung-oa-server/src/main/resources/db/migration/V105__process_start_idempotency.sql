-- 发起幂等：同一用户同一次提交（客户端生成的提交标识）只落一条审批。
-- 键可空：旧客户端不带键时照旧发起；MySQL 与 H2 的唯一索引都允许多个 NULL。
ALTER TABLE oa_process ADD COLUMN idempotency_key VARCHAR(64) DEFAULT NULL;
CREATE UNIQUE INDEX uk_oa_process_idempotency_key ON oa_process(user_id, idempotency_key);
