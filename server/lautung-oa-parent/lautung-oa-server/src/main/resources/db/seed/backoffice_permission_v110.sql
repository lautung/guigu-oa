-- 权限底座（ADR 0056，#221）的幂等种子：现有 6 页的按钮点、super_admin 与 admin 改挂。
-- 菜单行的目录/页面迁移在 V110__BackofficePermissionBase 里；这里只写 INSERT ... SELECT。
-- 每个插入都以 NOT EXISTS 守住，重复执行不会多出行。

-- ----------------------------
-- 用户管理：system:user:*
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增用户',2,'','','system:user:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysUser' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:user:add' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改用户',2,'','','system:user:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysUser' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:user:edit' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除用户',2,'','','system:user:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysUser' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:user:remove' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'分配角色',2,'','','system:user:assignRole','',4,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysUser' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:user:assignRole' AND b.is_deleted=0);

-- ----------------------------
-- 角色管理：system:role:*（菜单授权挂在角色页）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增角色',2,'','','system:role:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysRole' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:role:add' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改角色',2,'','','system:role:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysRole' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:role:edit' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除角色',2,'','','system:role:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysRole' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:role:remove' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'分配菜单',2,'','','system:role:assignAuth','',4,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysRole' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:role:assignAuth' AND b.is_deleted=0);

-- ----------------------------
-- 菜单管理：system:menu:*
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增菜单',2,'','','system:menu:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysMenu' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:menu:add' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改菜单',2,'','','system:menu:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysMenu' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:menu:edit' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除菜单',2,'','','system:menu:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='sysMenu' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:menu:remove' AND b.is_deleted=0);

-- ----------------------------
-- 审批类型：process:type:*（页面路径已迁到 /system/processSet/processType）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增审批类型',2,'','','process:type:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='/system/processSet/processType' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='process:type:add' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改审批类型',2,'','','process:type:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='/system/processSet/processType' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='process:type:edit' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除审批类型',2,'','','process:type:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='/system/processSet/processType' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='process:type:remove' AND b.is_deleted=0);

-- ----------------------------
-- 审批模板：process:template:*（上传与发布是两个独立动作）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增审批模板',2,'','','process:template:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='/system/processSet/processTemplate' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='process:template:add' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改审批模板',2,'','','process:template:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='/system/processSet/processTemplate' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='process:template:edit' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除审批模板',2,'','','process:template:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='/system/processSet/processTemplate' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='process:template:remove' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'上传流程定义',2,'','','process:template:upload','',4,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='/system/processSet/processTemplate' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='process:template:upload' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'发布审批模板',2,'','','process:template:publish','',5,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='/system/processSet/processTemplate' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='process:template:publish' AND b.is_deleted=0);

-- ----------------------------
-- 种子角色：super_admin 授予当时全部目录/页面/按钮；admin 从 config_admin 改挂。
-- config_admin 保留为受限样本（data_scope=4），本波不赋给任何用户。
-- ----------------------------
INSERT INTO sys_role (role_name,role_code,description,data_scope,create_time,update_time,is_deleted)
SELECT '超级管理员','super_admin','种子管理员：全部后台页面、按钮点与数据范围',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT 1 AS one) seed
LEFT JOIN sys_role existing ON existing.role_code='super_admin' AND existing.is_deleted=0
WHERE existing.id IS NULL;

INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;

UPDATE sys_role
SET data_scope=4,
    update_time=CURRENT_TIMESTAMP
WHERE role_code='config_admin'
  AND is_deleted=0
  AND (data_scope IS NULL OR data_scope <> 4);

DELETE FROM sys_user_role
WHERE user_id IN (SELECT id FROM sys_user WHERE username='admin' AND is_deleted=0)
  AND role_id IN (SELECT id FROM sys_role WHERE role_code='config_admin' AND is_deleted=0);

INSERT INTO sys_user_role (user_id,role_id,create_time,update_time,is_deleted)
SELECT u.id,r.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_user u
JOIN sys_role r ON r.role_code='super_admin' AND r.is_deleted=0
LEFT JOIN sys_user_role existing
       ON existing.user_id=u.id
      AND existing.role_id=r.id
      AND existing.is_deleted=0
WHERE u.username='admin'
  AND u.is_deleted=0
  AND existing.id IS NULL;
