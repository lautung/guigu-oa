-- 缓存监控与缓存列表页面种子（ADR 0060，#237）。
--
-- 由 V119 Java 迁移执行：在 V117 已种的 /monitor 目录下新增「缓存监控」（只读，无按钮点）
-- 与「缓存列表」（挂 monitor:cache:clean 按钮点）两个页面行并显式补授 super_admin。
-- 页面路径是绝对路径（V111 规则）；按钮行 path/component 为空，perms 必填且唯一。
-- 所有插入都以 NOT EXISTS 守住，空库与既有库重复执行都不会多出行。

-- ----------------------------
-- 页面：/monitor/cache（组件 monitor/cache/index，只读）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'缓存监控',1,'/monitor/cache','monitor/cache/index','','',4,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/monitor' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/monitor/cache' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 页面：/monitor/cacheList（组件 monitor/cacheList/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'缓存列表',1,'/monitor/cacheList','monitor/cacheList/index','','',5,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/monitor' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/monitor/cacheList' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 按钮点：monitor:cache:clean（挂在缓存列表下）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'清理缓存',2,'','','monitor:cache:clean','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/monitor/cacheList' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='monitor:cache:clean' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=1 AND m.path IN ('/monitor/cache','/monitor/cacheList'))
    OR (m.type=2 AND m.perms='monitor:cache:clean')
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
