-- 审批人规则与解析合同夹具（ADR 0065/0069，#285）：部门/岗位/角色作为审批人来源。
-- 只动本票夹具行（8xx 高位 id），不碰其他合同类的种子。

DELETE FROM sys_dept;
INSERT INTO sys_dept (id, parent_id, ancestors, name, leader_user_id, sort_value, status, is_deleted) VALUES
  (801, 0, ',0,801,', '研发部', 3, 1, 1, 0),
  (802, 0, ',0,802,', '无主部门', NULL, 2, 1, 0),
  (803, 0, ',0,803,', '停用部门', 3, 3, 0, 0),
  (804, 0, ',0,804,', '自审部门', 2, 4, 1, 0);

UPDATE sys_user SET dept_id = NULL;
UPDATE sys_user SET dept_id = 804 WHERE id = 2;
UPDATE sys_user SET dept_id = 801 WHERE id IN (3, 4);
UPDATE sys_user SET dept_id = 802 WHERE id = 5;

DELETE FROM sys_post WHERE id IN (811, 812);
INSERT INTO sys_post (id, post_code, name, sort_value, status, is_deleted) VALUES
  (811, 'manager', '经理岗', 1, 1, 0),
  (812, 'idle', '停用岗', 2, 0, 0);
DELETE FROM sys_user_post WHERE user_id IN (3, 4);
INSERT INTO sys_user_post (user_id, post_id, is_deleted) VALUES (3, 811, 0), (4, 811, 0);

DELETE FROM sys_role WHERE id IN (821, 822, 823, 825);
INSERT INTO sys_role (id, role_name, role_code, is_deleted) VALUES
  (821, '审批角色', 'approver_pair', 0),
  (822, '已删角色', 'approver_deleted', 1),
  (823, '三人组', 'approver_trio', 0),
  (825, '仅申请人', 'approver_self_only', 0);
DELETE FROM sys_user_role WHERE role_id IN (821, 822, 823, 825);
INSERT INTO sys_user_role (role_id, user_id, is_deleted) VALUES
  (821, 3, 0), (821, 4, 0),
  (823, 3, 0), (823, 4, 0), (823, 5, 0),
  (825, 2, 0);

DELETE FROM oa_process_approver_rule;
