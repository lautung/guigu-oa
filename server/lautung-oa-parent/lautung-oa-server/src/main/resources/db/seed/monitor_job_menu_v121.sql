-- 定时任务页面种子（ADR 0060，#238）。
--
-- 由 V121 Java 迁移执行：在 V117 已种的 /monitor 目录下新增「定时任务」页面行与
-- monitor:job:edit / monitor:job:run 两个按钮点，并显式补授 super_admin。
-- 任务日志读不设点（页面授予覆盖读）；页面路径是绝对路径（V111 规则）；
-- 按钮行 path/component 为空，perms 必填且唯一。所有插入都以 NOT EXISTS 守住，
-- 空库与既有库重复执行都不会多出行。

-- ----------------------------
-- 页面：/monitor/job（组件 monitor/job/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'定时任务',1,'/monitor/job','monitor/job/index','','',6,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/monitor' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/monitor/job' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 按钮点：monitor:job:edit（启停 / 改 cron / 并发 / 备注）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改任务',2,'','','monitor:job:edit','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/monitor/job' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='monitor:job:edit' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：monitor:job:run（手动执行）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'手动执行',2,'','','monitor:job:run','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/monitor/job' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='monitor:job:run' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=1 AND m.path='/monitor/job')
    OR (m.type=2 AND m.perms IN ('monitor:job:edit','monitor:job:run'))
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
