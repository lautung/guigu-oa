-- 演示考勤种子（#284，ADR 0073 §种子草案）：只在 demo profile 下执行。
--
-- 版本取 V141 而不是演示序列的 V102：demo-migration 与 db/migration 共用同一个
-- Flyway history，V102 已被常规迁移占用（Flyway 会以「Found more than one migration
-- with version 102」拒绝启动）；V141 是考勤表（V139/V140）之后的下一可用版本。
--
-- 全部插入由 NOT EXISTS 守住，脚本重放不新增行。日期用
-- CAST(CONCAT(YEAR(CURRENT_DATE), '-MM-DD') AS DATE) 取「当年」，MySQL 与 H2 同形。

-- ----------------------------
-- 演示部门：现有演示账号都没有部门，部门负责人审批需要「alice 提交 → bob 审批」闭环。
-- bob 是负责人；alice/bob/carol 划入同一部门。bob 不存在时整段不写。
-- ----------------------------
INSERT INTO sys_dept (parent_id, ancestors, name, leader_user_id, sort_value, status,
                      create_time, update_time, is_deleted)
SELECT 0, '0', '演示部门', u.id, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
WHERE u.username = 'bob' AND u.status = 1 AND u.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_dept d
                  WHERE d.name = '演示部门' AND d.parent_id = 0 AND d.is_deleted = 0);

-- ancestors 的唯一写入形状是「,0,<id>,」（SysDeptServiceImpl）：插入时拿不到自增 id，
-- 这里按 id 回填；重放时已正确的行不动。
UPDATE sys_dept
SET ancestors = CONCAT(',0,', id, ','), update_time = CURRENT_TIMESTAMP
WHERE name = '演示部门' AND parent_id = 0 AND is_deleted = 0
  AND (ancestors IS NULL OR ancestors <> CONCAT(',0,', id, ','));

UPDATE sys_dept
SET leader_user_id = (SELECT u.id FROM sys_user u
                      WHERE u.username = 'bob' AND u.status = 1 AND u.is_deleted = 0),
    update_time = CURRENT_TIMESTAMP
WHERE name = '演示部门' AND parent_id = 0 AND is_deleted = 0 AND leader_user_id IS NULL
  AND EXISTS (SELECT 1 FROM sys_user u
              WHERE u.username = 'bob' AND u.status = 1 AND u.is_deleted = 0);

UPDATE sys_user
SET dept_id = (SELECT d.id FROM sys_dept d
               WHERE d.name = '演示部门' AND d.parent_id = 0 AND d.is_deleted = 0),
    update_time = CURRENT_TIMESTAMP
WHERE username IN ('alice', 'bob', 'carol') AND is_deleted = 0 AND dept_id IS NULL
  AND EXISTS (SELECT 1 FROM sys_dept d
              WHERE d.name = '演示部门' AND d.parent_id = 0 AND d.is_deleted = 0);

-- ----------------------------
-- 演示班次与考勤组：9:00–18:00、午休 12:00–13:00、周一至周五；alice 入组。
-- ----------------------------
INSERT INTO attendance_shift (name, start_time, end_time, lunch_start, lunch_end,
                              grace_minutes, remark, create_time, update_time, is_deleted)
SELECT '演示班次', '09:00:00', '18:00:00', '12:00:00', '13:00:00',
       0, '演示用标准班', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM attendance_shift WHERE name = '演示班次' AND is_deleted = 0);

INSERT INTO attendance_group (name, shift_id, workdays, remark, create_time, update_time, is_deleted)
SELECT '演示考勤组', s.id, '1,2,3,4,5', '演示用考勤组', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM attendance_shift s
WHERE s.name = '演示班次' AND s.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM attendance_group g
                  WHERE g.name = '演示考勤组' AND g.is_deleted = 0)
ORDER BY s.id LIMIT 1;

INSERT INTO attendance_group_member (group_id, user_id, create_time, update_time)
SELECT g.id, u.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM attendance_group g
JOIN sys_user u ON u.username = 'alice'
WHERE g.name = '演示考勤组' AND g.is_deleted = 0
  AND u.status = 1 AND u.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM attendance_group_member m WHERE m.user_id = u.id);

-- ----------------------------
-- 当年节假日：休假日（元旦/劳动节/国庆节）与补班日各若干。
-- ----------------------------
INSERT INTO attendance_holiday (holiday_date, name, day_type, create_time, update_time)
SELECT CAST(CONCAT(YEAR(CURRENT_DATE), '-01-01') AS DATE), '元旦', 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM attendance_holiday
                  WHERE holiday_date = CAST(CONCAT(YEAR(CURRENT_DATE), '-01-01') AS DATE));

INSERT INTO attendance_holiday (holiday_date, name, day_type, create_time, update_time)
SELECT CAST(CONCAT(YEAR(CURRENT_DATE), '-05-01') AS DATE), '劳动节', 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM attendance_holiday
                  WHERE holiday_date = CAST(CONCAT(YEAR(CURRENT_DATE), '-05-01') AS DATE));

INSERT INTO attendance_holiday (holiday_date, name, day_type, create_time, update_time)
SELECT CAST(CONCAT(YEAR(CURRENT_DATE), '-05-02') AS DATE), '劳动节', 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM attendance_holiday
                  WHERE holiday_date = CAST(CONCAT(YEAR(CURRENT_DATE), '-05-02') AS DATE));

INSERT INTO attendance_holiday (holiday_date, name, day_type, create_time, update_time)
SELECT CAST(CONCAT(YEAR(CURRENT_DATE), '-05-03') AS DATE), '劳动节', 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM attendance_holiday
                  WHERE holiday_date = CAST(CONCAT(YEAR(CURRENT_DATE), '-05-03') AS DATE));

INSERT INTO attendance_holiday (holiday_date, name, day_type, create_time, update_time)
SELECT CAST(CONCAT(YEAR(CURRENT_DATE), '-10-01') AS DATE), '国庆节', 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM attendance_holiday
                  WHERE holiday_date = CAST(CONCAT(YEAR(CURRENT_DATE), '-10-01') AS DATE));

INSERT INTO attendance_holiday (holiday_date, name, day_type, create_time, update_time)
SELECT CAST(CONCAT(YEAR(CURRENT_DATE), '-10-02') AS DATE), '国庆节', 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM attendance_holiday
                  WHERE holiday_date = CAST(CONCAT(YEAR(CURRENT_DATE), '-10-02') AS DATE));

INSERT INTO attendance_holiday (holiday_date, name, day_type, create_time, update_time)
SELECT CAST(CONCAT(YEAR(CURRENT_DATE), '-10-03') AS DATE), '国庆节', 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM attendance_holiday
                  WHERE holiday_date = CAST(CONCAT(YEAR(CURRENT_DATE), '-10-03') AS DATE));

INSERT INTO attendance_holiday (holiday_date, name, day_type, create_time, update_time)
SELECT CAST(CONCAT(YEAR(CURRENT_DATE), '-02-15') AS DATE), '春节调休补班', 2,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM attendance_holiday
                  WHERE holiday_date = CAST(CONCAT(YEAR(CURRENT_DATE), '-02-15') AS DATE));

INSERT INTO attendance_holiday (holiday_date, name, day_type, create_time, update_time)
SELECT CAST(CONCAT(YEAR(CURRENT_DATE), '-10-10') AS DATE), '国庆调休补班', 2,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM attendance_holiday
                  WHERE holiday_date = CAST(CONCAT(YEAR(CURRENT_DATE), '-10-10') AS DATE));

-- ----------------------------
-- 年假发放示例：alice 与 admin 各 40 小时（5 天），审批通过按天扣减。
-- ----------------------------
INSERT INTO attendance_leave_ledger (user_id, leave_type_id, entry_type, change_hours,
                                     business_type, business_id, operator_user_id, remark,
                                     create_time, update_time, is_deleted)
SELECT u.id, t.id, 1, 40.00, NULL, NULL, u.id, '演示年假发放（5 天）',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
JOIN attendance_leave_type t ON t.name = '年假' AND t.is_deleted = 0
WHERE u.username IN ('alice', 'admin') AND u.status = 1 AND u.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM attendance_leave_ledger l
                  WHERE l.user_id = u.id AND l.leave_type_id = t.id
                    AND l.entry_type = 1 AND l.remark = '演示年假发放（5 天）'
                    AND l.is_deleted = 0);

-- ----------------------------
-- 考勤域业务模板：请假/加班各一份，单节点「部门负责人审批」（ADR 0065/0069）。
-- 种成未发布，启动时由 DemoApprovalPublisher 走正常发布流程补产物、发布记录与引擎部署。
-- ----------------------------
INSERT INTO oa_process_template (
  name, icon_url, process_type_id, form_props, form_options, description,
  process_definition_key, process_definition_path, status, create_time, update_time, is_deleted
)
SELECT
  '演示考勤请假审批', '', t.id,
  '[]',
  '{"submitBtn":true,"form":{"labelPosition":"top"}}',
  '考勤请假单的业务审批模板：部门负责人单节点审批。',
  'attendanceLeave', 'processes/attendance-leave.zip', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM oa_process_type t
WHERE t.name = '演示审批' AND t.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM oa_process_template p
                  WHERE p.process_definition_key = 'attendanceLeave' AND p.is_deleted = 0);

INSERT INTO oa_process_template (
  name, icon_url, process_type_id, form_props, form_options, description,
  process_definition_key, process_definition_path, status, create_time, update_time, is_deleted
)
SELECT
  '演示考勤加班审批', '', t.id,
  '[]',
  '{"submitBtn":true,"form":{"labelPosition":"top"}}',
  '考勤加班单的业务审批模板：部门负责人单节点审批。',
  'attendanceOvertime', 'processes/attendance-overtime.zip', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM oa_process_type t
WHERE t.name = '演示审批' AND t.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM oa_process_template p
                  WHERE p.process_definition_key = 'attendanceOvertime' AND p.is_deleted = 0);

-- 审批人规则：单节点 DEPT_LEADER（目标为空，按发起人所在部门解析）。
INSERT INTO oa_process_approver_rule (process_template_id, task_definition_key, source_type,
                                      target_id, create_time, update_time, is_deleted)
SELECT p.id, 'deptLeaderReview', 'DEPT_LEADER', NULL,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM oa_process_template p
WHERE p.process_definition_key = 'attendanceLeave' AND p.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM oa_process_approver_rule r
                  WHERE r.process_template_id = p.id
                    AND r.task_definition_key = 'deptLeaderReview');

INSERT INTO oa_process_approver_rule (process_template_id, task_definition_key, source_type,
                                      target_id, create_time, update_time, is_deleted)
SELECT p.id, 'deptLeaderReview', 'DEPT_LEADER', NULL,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM oa_process_template p
WHERE p.process_definition_key = 'attendanceOvertime' AND p.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM oa_process_approver_rule r
                  WHERE r.process_template_id = p.id
                    AND r.task_definition_key = 'deptLeaderReview');

-- 业务键映射（ADR 0067）：attendance.leave / attendance.overtime → 各自模板。
INSERT INTO oa_business_template (business_type, process_template_id, create_time, update_time, is_deleted)
SELECT 'attendance.leave', p.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM oa_process_template p
WHERE p.process_definition_key = 'attendanceLeave' AND p.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM oa_business_template b WHERE b.business_type = 'attendance.leave')
ORDER BY p.id LIMIT 1;

INSERT INTO oa_business_template (business_type, process_template_id, create_time, update_time, is_deleted)
SELECT 'attendance.overtime', p.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM oa_process_template p
WHERE p.process_definition_key = 'attendanceOvertime' AND p.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM oa_business_template b WHERE b.business_type = 'attendance.overtime')
ORDER BY p.id LIMIT 1;
