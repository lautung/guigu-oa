-- 岗位管理菜单种子（#248）。
--
-- 由 V124 Java 迁移执行：在 /system 目录下新增「岗位管理」页面行与
-- system:post:add / system:post:edit / system:post:remove 三个按钮点，并显式补授 super_admin。
-- 页面路径是绝对路径（V111 规则）；按钮行 path/component 为空，perms 必填且唯一。
-- 岗位只是职务标签，不产生授权、不参与审批分配、不套数据范围。
-- 所有插入都以 NOT EXISTS 守住，空库与既有库重复执行都不会多出行。

-- ----------------------------
-- 页面：/system/sysPost（组件 system/sysPost/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'岗位管理',1,'/system/sysPost','system/sysPost/index','','',5,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/system' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/system/sysPost' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 按钮点：system:post:add（新增）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增岗位',2,'','','system:post:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/sysPost' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:post:add' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：system:post:edit（修改）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改岗位',2,'','','system:post:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/sysPost' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:post:edit' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：system:post:remove（删除）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除岗位',2,'','','system:post:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/sysPost' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:post:remove' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=1 AND m.path='/system/sysPost')
    OR (m.type=2 AND m.perms IN ('system:post:add','system:post:edit','system:post:remove'))
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
