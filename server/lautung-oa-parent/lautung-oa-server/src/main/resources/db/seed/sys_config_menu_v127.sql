-- 参数设置菜单与首批参数种子（#252，ADR 0062）。
--
-- 由 V127 Java 迁移执行：在 /system 目录下新增「参数设置」页面行与
-- system:config:add / system:config:edit / system:config:remove 三个按钮点，
-- 显式补授 super_admin，并种入首批参数 sys.account.registerUser=false。
-- 页面路径是绝对路径（V111 规则）；按钮行 path/component 为空，perms 必填且唯一。
-- 只种有消费方的键（注册开关），不种空壳参数。
-- 所有插入都以 NOT EXISTS 守住，空库与既有库重复执行都不会多出行。

-- ----------------------------
-- 页面：/system/config（组件 system/config/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'参数设置',1,'/system/config','system/config/index','','',6,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system' AND p.parent_id=0 AND p.type=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c
                  WHERE c.path='/system/config' AND c.type=1 AND c.is_deleted=0);

-- ----------------------------
-- 按钮点：system:config:add（新增）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增参数',2,'','','system:config:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/config' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:config:add' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：system:config:edit（修改，手动刷新缓存复用该点）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改参数',2,'','','system:config:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/config' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:config:edit' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：system:config:remove（删除）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除参数',2,'','','system:config:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/config' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:config:remove' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=1 AND m.path='/system/config')
    OR (m.type=2 AND m.perms IN ('system:config:add','system:config:edit','system:config:remove'))
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;

-- ----------------------------
-- 首批参数：注册开关（默认关闭，注册页由它打开）
-- ----------------------------
INSERT INTO sys_config (config_key,config_value,config_name,value_type,builtin,remark,create_time,update_time,is_deleted)
SELECT 'sys.account.registerUser','false','是否开放注册','boolean',1,'关闭时注册页不可用（ADR 0062）',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='sys.account.registerUser');
