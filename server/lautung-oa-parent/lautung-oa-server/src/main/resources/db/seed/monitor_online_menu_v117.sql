-- 系统监控目录与在线用户菜单种子（ADR 0060，#235）。
--
-- 由 V117 Java 迁移执行：只新增 /monitor 目录、/monitor/online 页面与强退按钮点，
-- 并显式补授 super_admin。其余监控页面随各自服务端票种下，本文件不预种。
-- 页面路径是绝对路径（V111 规则）；按钮行 path/component 为空，perms 必填且唯一。
-- 所有插入都以 NOT EXISTS 守住，空库与既有库重复执行都不会多出行。

-- ----------------------------
-- 目录：/monitor（系统监控）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT 0,'系统监控',0,'/monitor','Layout','','el-icon-monitor',4,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m
                  WHERE m.path='/monitor' AND m.parent_id=0 AND m.is_deleted=0);

-- ----------------------------
-- 页面：/monitor/online（组件 monitor/online/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'在线用户',1,'/monitor/online','monitor/online/index','','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/monitor' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/monitor/online' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 按钮点：monitor:online:forceLogout
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'强制下线',2,'','','monitor:online:forceLogout','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/monitor/online' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='monitor:online:forceLogout' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=1 AND m.path='/monitor/online')
    OR (m.type=2 AND m.perms='monitor:online:forceLogout')
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
