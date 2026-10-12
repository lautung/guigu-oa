-- 演示账号只在 demo profile 下创建；密码与基础账号一致，均为 BCrypt('111111')。
INSERT INTO sys_user (username, password, name, phone, status, create_time, update_time, is_deleted)
SELECT 'alice', '$2b$10$JF/wulA6oTURVNZYCgyNiuJFpnogF58MsYwPpO8dL/EWDQI4TuiGa', '艾丽丝', '13800000002', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'alice');

INSERT INTO sys_user (username, password, name, phone, status, create_time, update_time, is_deleted)
SELECT 'bob', '$2b$10$JF/wulA6oTURVNZYCgyNiuJFpnogF58MsYwPpO8dL/EWDQI4TuiGa', '鲍勃', '13800000003', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'bob');

INSERT INTO sys_user (username, password, name, phone, status, create_time, update_time, is_deleted)
SELECT 'carol', '$2b$10$JF/wulA6oTURVNZYCgyNiuJFpnogF58MsYwPpO8dL/EWDQI4TuiGa', '卡萝', '13800000004', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'carol');

INSERT INTO oa_process_type (name, description, create_time, update_time, is_deleted)
SELECT '演示审批', 'Compose 演示审批', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM oa_process_type WHERE name = '演示审批' AND is_deleted = 0);

INSERT INTO oa_process_template (
  name, icon_url, process_type_id, form_props, form_options, description,
  process_definition_key, process_definition_path, status, create_time, update_time, is_deleted
)
SELECT
  '演示请假申请', '', t.id,
  '[{"type":"input","field":"reason","title":"请假原因","$required":true},{"type":"input","field":"days","title":"请假天数","$required":true,"props":{"type":"number"}}]',
  '{"submitBtn":true,"form":{"labelPosition":"top"}}', '用于演示待处理、已结束、抄送和附件的审批。',
  'demoApproval', 'processes/demo-approval.zip', 1, NOW(), NOW(), 0
FROM oa_process_type t
WHERE t.name = '演示审批' AND t.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM oa_process_template p WHERE p.process_definition_key = 'demoApproval' AND p.is_deleted = 0);

INSERT INTO oa_process_cc_rule (process_definition_key, task_definition_key, user_id, is_deleted)
SELECT 'demoApproval', 'bobReview', u.id, 0
FROM sys_user u
WHERE u.username = 'carol' AND u.status = 1 AND u.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM oa_process_cc_rule r
    WHERE r.process_definition_key = 'demoApproval'
      AND r.task_definition_key = 'bobReview'
      AND r.user_id = u.id
  );
