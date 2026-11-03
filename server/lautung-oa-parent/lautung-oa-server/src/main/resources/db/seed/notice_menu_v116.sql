-- 通知公告菜单种子（ADR 0055/0056，#218）。
--
-- 由 V116 Java 迁移执行：三张表的 DDL/索引在 Java 里做元数据幂等（MySQL 8 不支持
-- CREATE INDEX IF NOT EXISTS），本文件只放 DML，全部由 NOT EXISTS 守住，空库与既有库
-- 重复执行都不会多出行。
--
-- 页面路径是绝对路径（V111 规则）；按钮行 path/component 为空，perms 必填且唯一。
-- 只种 add/edit/remove/publish：读由页面授予覆盖，不种 system:notice:query（ADR 0056）。
-- 不种客户端路由/页面（客户端归 #219/#220）。

-- ----------------------------
-- 页面：/system/notice（组件 system/notice/list）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'通知公告',1,'/system/notice','system/notice/list','','',12,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system' AND p.parent_id=0 AND p.type=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu n
                  WHERE n.path='/system/notice' AND n.type=1 AND n.is_deleted=0);

-- ----------------------------
-- 按钮点：system:notice:add / edit / remove / publish（publish 同时管撤回）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增公告',2,'','','system:notice:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/notice' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:notice:add' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'编辑公告',2,'','','system:notice:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/notice' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:notice:edit' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除公告',2,'','','system:notice:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/notice' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:notice:remove' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'发布公告',2,'','','system:notice:publish','',4,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/notice' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:notice:publish' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=1 AND m.path='/system/notice')
    OR (m.type=2 AND m.perms IN ('system:notice:add','system:notice:edit','system:notice:remove','system:notice:publish'))
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
