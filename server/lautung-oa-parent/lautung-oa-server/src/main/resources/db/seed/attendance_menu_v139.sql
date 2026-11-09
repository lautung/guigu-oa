-- 考勤底座菜单与假期类型种子（#278，ADR 0073）。
--
-- 由 V139 Java 迁移执行：新增 /business/attendance 目录、五页（考勤组与班次/打卡记录/
-- 补卡处理/假期类型与发放/月度统计）与八个按钮点（attendance:group:add|edit|remove、
-- attendance:record:repair、attendance:type:add|edit|remove、attendance:ledger:grant），
-- 显式补授 super_admin，并种入假期类型五项预置（年假/事假/病假/调休/婚丧产假）。
-- 页面路径是绝对路径（V111 规则）；按钮行 path/component 为空，perms 必填且唯一。
-- 所有插入都以 NOT EXISTS 守住，空库与既有库重复执行都不会多出行。
-- super_admin 只补授页面与按钮点：目录祖先在读取时由 BackofficeGrant 自动补齐
-- （与 V117 的 /monitor 同一写法）。

-- ----------------------------
-- 目录：/business/attendance（考勤管理）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT 0,'考勤管理',0,'/business/attendance','Layout','','',6,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m
                  WHERE m.path='/business/attendance' AND m.parent_id=0 AND m.type=0 AND m.is_deleted=0);

-- ----------------------------
-- 页面：/business/attendance/group（考勤组与班次，组件 business/attendance/group/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'考勤组与班次',1,'/business/attendance/group','business/attendance/group/index','','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/business/attendance' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/business/attendance/group' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 页面：/business/attendance/record（打卡记录）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'打卡记录',1,'/business/attendance/record','business/attendance/record/index','','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/business/attendance' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/business/attendance/record' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 页面：/business/attendance/repair（补卡处理）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'补卡处理',1,'/business/attendance/repair','business/attendance/repair/index','','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/business/attendance' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/business/attendance/repair' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 页面：/business/attendance/leave（假期类型与发放）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'假期类型与发放',1,'/business/attendance/leave','business/attendance/leave/index','','',4,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/business/attendance' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/business/attendance/leave' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 页面：/business/attendance/stat（月度统计）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'月度统计',1,'/business/attendance/stat','business/attendance/stat/index','','',5,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/business/attendance' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/business/attendance/stat' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 按钮点：attendance:group:add（考勤组与班次页的班次/考勤组/节假日新增共用）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增考勤配置',2,'','','attendance:group:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/business/attendance/group' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='attendance:group:add' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：attendance:group:edit（修改；成员管理也挂该点）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改考勤配置',2,'','','attendance:group:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/business/attendance/group' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='attendance:group:edit' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：attendance:group:remove（删除）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除考勤配置',2,'','','attendance:group:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/business/attendance/group' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='attendance:group:remove' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：attendance:record:repair（补卡，挂补卡处理页）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'补卡',2,'','','attendance:record:repair','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/business/attendance/repair' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='attendance:record:repair' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：attendance:type:add（假期类型新增，挂假期类型与发放页）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增假期类型',2,'','','attendance:type:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/business/attendance/leave' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='attendance:type:add' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：attendance:type:edit（假期类型修改）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改假期类型',2,'','','attendance:type:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/business/attendance/leave' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='attendance:type:edit' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：attendance:type:remove（假期类型删除）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除假期类型',2,'','','attendance:type:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/business/attendance/leave' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='attendance:type:remove' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：attendance:ledger:grant（假期发放）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'假期发放',2,'','','attendance:ledger:grant','',4,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/business/attendance/leave' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='attendance:ledger:grant' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=1 AND m.path IN ('/business/attendance/group','/business/attendance/record',
                                '/business/attendance/repair','/business/attendance/leave',
                                '/business/attendance/stat'))
    OR (m.type=2 AND m.perms IN ('attendance:group:add','attendance:group:edit','attendance:group:remove',
                                 'attendance:record:repair','attendance:type:add','attendance:type:edit',
                                 'attendance:type:remove','attendance:ledger:grant'))
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;

-- ----------------------------
-- 假期类型五项预置（ADR 0073）：年假（余额）、事假、病假、调休（余额+加班入账目标）、
-- 婚丧产假。调休的加班入账目标全局唯一由 service 校验（#280 票），种子直接给唯一一行。
-- ----------------------------
INSERT INTO attendance_leave_type (name,balance_required,overtime_credit,status,sort_no,remark,create_time,update_time,is_deleted)
SELECT '年假',1,0,1,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM attendance_leave_type WHERE name='年假' AND is_deleted=0);

INSERT INTO attendance_leave_type (name,balance_required,overtime_credit,status,sort_no,remark,create_time,update_time,is_deleted)
SELECT '事假',0,0,1,2,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM attendance_leave_type WHERE name='事假' AND is_deleted=0);

INSERT INTO attendance_leave_type (name,balance_required,overtime_credit,status,sort_no,remark,create_time,update_time,is_deleted)
SELECT '病假',0,0,1,3,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM attendance_leave_type WHERE name='病假' AND is_deleted=0);

INSERT INTO attendance_leave_type (name,balance_required,overtime_credit,status,sort_no,remark,create_time,update_time,is_deleted)
SELECT '调休',1,1,1,4,'加班入账目标（全局唯一）',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM attendance_leave_type WHERE name='调休' AND is_deleted=0);

INSERT INTO attendance_leave_type (name,balance_required,overtime_credit,status,sort_no,remark,create_time,update_time,is_deleted)
SELECT '婚丧产假',0,0,1,5,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM attendance_leave_type WHERE name='婚丧产假' AND is_deleted=0);
