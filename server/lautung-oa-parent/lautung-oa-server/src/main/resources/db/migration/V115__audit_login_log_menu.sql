-- 登录日志菜单种子（ADR 0054/0056，#216）：复用 V113 的「日志管理」目录（log）与
-- 「操作日志」页，只新增「登录日志」页面以及删除/清空/解锁三个按钮点；新菜单显式补授给
-- super_admin（管理员不做身份旁路）。
--
-- 不插第二个 log 目录；不种客户端路由/页面（客户端页归 #217）。所有插入都以 NOT EXISTS
-- 守住，空库与既有库重复执行都不会多出行。页面路径是绝对路径（V111 规则）；按钮行
-- path/component 为空，perms 必填且唯一。

-- ----------------------------
-- 页面：/system/log/loginLog（组件 system/loginLog/list）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'登录日志',1,'/system/log/loginLog','system/loginLog/list','','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
JOIN sys_menu r ON r.id=d.parent_id
               AND r.path='/system' AND r.parent_id=0 AND r.type=0 AND r.is_deleted=0
WHERE d.path='log' AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/system/log/loginLog' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 按钮点：system:loginLog:remove / system:loginLog:clean / system:loginLog:unlock
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除登录日志',2,'','','system:loginLog:remove','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/log/loginLog' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:loginLog:remove' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'清空登录日志',2,'','','system:loginLog:clean','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/log/loginLog' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:loginLog:clean' AND b.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'解锁账号',2,'','','system:loginLog:unlock','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/log/loginLog' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:loginLog:unlock' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=1 AND m.path='/system/log/loginLog')
    OR (m.type=2 AND m.perms IN ('system:loginLog:remove','system:loginLog:clean','system:loginLog:unlock'))
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
