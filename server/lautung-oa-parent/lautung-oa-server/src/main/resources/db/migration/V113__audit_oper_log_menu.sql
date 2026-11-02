-- 日志管理菜单种子（ADR 0056，#214）：系统管理下新增「日志管理」目录与「操作日志」页面，
-- 以及删除/清空两个按钮点；新菜单显式补授给 super_admin（管理员不做身份旁路）。
--
-- 只种操作日志页，不种登录日志页（#216 自己的迁移）；不种客户端路由/页面。
-- 所有插入都以 NOT EXISTS 守住，空库与既有库重复执行都不会多出行。
-- 页面路径是绝对路径（V111 规则）；按钮行 path/component 为空，perms 必填且唯一。

-- ----------------------------
-- 目录：/system 下的「日志管理」
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'日志管理',0,'log','','','',11,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system' AND p.parent_id=0 AND p.type=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu d
                  WHERE d.parent_id=p.id AND d.path='log' AND d.type=0 AND d.is_deleted=0);

-- ----------------------------
-- 页面：/system/log/operLog（组件 system/operLog/list）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'操作日志',1,'/system/log/operLog','system/operLog/list','','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
JOIN sys_menu r ON r.id=d.parent_id
               AND r.path='/system' AND r.parent_id=0 AND r.type=0 AND r.is_deleted=0
WHERE d.path='log' AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/system/log/operLog' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 按钮点：system:operLog:remove / system:operLog:clean
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除操作日志',2,'','','system:operLog:remove','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/log/operLog' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:operLog:remove' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'清空操作日志',2,'','','system:operLog:clean','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/log/operLog' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:operLog:clean' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的目录/页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=0 AND m.path='log' AND m.parent_id=(
           SELECT id FROM sys_menu WHERE path='/system' AND parent_id=0 AND is_deleted=0 LIMIT 1))
    OR (m.type=1 AND m.path='/system/log/operLog')
    OR (m.type=2 AND m.perms IN ('system:operLog:remove','system:operLog:clean'))
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
