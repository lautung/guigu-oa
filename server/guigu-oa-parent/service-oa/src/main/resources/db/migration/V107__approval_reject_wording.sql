-- 审批流转文案对齐 GLOSSARY「处理审批＝通过或拒绝」口径（#172，ADR 0043）：拒绝侧不再用「驳回」。
-- 「发起申请」流转记录与审批动作记录共享 status=1（ApprovalWriter 写入），不能按 status 映射，
-- 只按 description 精确匹配订正，其余描述（发起申请、等待审批）不动。
-- 历史订正：不筛 is_deleted，不改 create_time/update_time（无 ON UPDATE 子句，UPDATE 天然不触碰）。
UPDATE oa_process_record SET description = '已拒绝' WHERE description = '驳回';

UPDATE oa_process SET description = '审批完成（拒绝）'
WHERE description = '审批完成（驳回）' AND status = -1;
