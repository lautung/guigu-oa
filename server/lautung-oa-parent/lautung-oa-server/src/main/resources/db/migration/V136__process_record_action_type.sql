-- 审批动作层底座（ADR 0068，#287）：oa_process_record 从「流转文案账本」升级为「动作账本」。
-- action_type 是已处理白名单（APPROVE/REJECT）与动作语义的唯一判据，description 继续唯一拥有
-- 人类可读文案（ADR 0043）；target 列服务「动作对象是单个人」的动作（转办；催办为多人集合，
-- 只落 target_user_name 顿号连接，target_user_id 留空）。
ALTER TABLE oa_process_record ADD COLUMN action_type VARCHAR(20) DEFAULT NULL;
ALTER TABLE oa_process_record ADD COLUMN target_user_id BIGINT DEFAULT NULL;
ALTER TABLE oa_process_record ADD COLUMN target_user_name VARCHAR(200) DEFAULT NULL;

-- 历史行按 description 精确回填（幂等可重放：按 description 判定、只填空值）。
-- 不筛 is_deleted：软删行的动作类型同样是事实；不改 create_time/update_time（无 ON UPDATE 子句）。
UPDATE oa_process_record SET action_type = 'INITIATE' WHERE action_type IS NULL AND description = '发起申请';
UPDATE oa_process_record SET action_type = 'APPROVE' WHERE action_type IS NULL AND description = '已通过';
UPDATE oa_process_record SET action_type = 'REJECT' WHERE action_type IS NULL AND description = '已拒绝';
-- 其余（含 description 为空的行）一律 OTHER：回填后 action_type 不再有 NULL，
-- 白名单谓词对历史行的判定因此是全量的、不依赖 description。
UPDATE oa_process_record SET action_type = 'OTHER' WHERE action_type IS NULL;
