-- 服务监控与数据监控页面种子（ADR 0060，#236）。
--
-- 由 V118 Java 迁移执行：在 V117 已种的 /monitor 目录下新增「服务监控」「数据监控」
-- 两个只读页面行并显式补授 super_admin。无按钮行——ADR 0056 读不设点，页面授予覆盖读。
-- 页面路径是绝对路径（V111 规则）；所有插入都以 NOT EXISTS 守住，
-- 空库与既有库重复执行都不会多出行。

-- ----------------------------
-- 页面：/monitor/server（组件 monitor/server/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'服务监控',1,'/monitor/server','monitor/server/index','','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/monitor' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/monitor/server' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 页面：/monitor/data（组件 monitor/data/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'数据监控',1,'/monitor/data','monitor/data/index','','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/monitor' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/monitor/data' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的两个页面，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND m.type=1
      AND m.path IN ('/monitor/server','/monitor/data')
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
