-- 系统工具目录与接口文档页面种子（ADR 0061，#246）。
--
-- 由 V122 Java 迁移执行：新增 /tool 目录与 /tool/api-doc 页面，并显式补授 super_admin。
-- 系统工具本期只有这一张页面，不设按钮点（只读页无写操作，ADR 0061）；
-- 表单构建不另设页，由「审批设置 → 模板设置」承载。
-- 页面路径是绝对路径（V111 规则）；所有插入都以 NOT EXISTS 守住，
-- 空库与既有库重复执行都不会多出行。

-- ----------------------------
-- 目录：/tool（系统工具）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT 0,'系统工具',0,'/tool','Layout','','el-icon-s-tools',5,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m
                  WHERE m.path='/tool' AND m.parent_id=0 AND m.is_deleted=0);

-- ----------------------------
-- 页面：/tool/api-doc（组件 tool/api-doc/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT d.id,'接口文档',1,'/tool/api-doc','tool/api-doc/index','','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu d
WHERE d.path='/tool' AND d.parent_id=0 AND d.type=0 AND d.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu p
                  WHERE p.path='/tool/api-doc' AND p.type=1 AND p.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND m.type=1 AND m.path='/tool/api-doc'
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
