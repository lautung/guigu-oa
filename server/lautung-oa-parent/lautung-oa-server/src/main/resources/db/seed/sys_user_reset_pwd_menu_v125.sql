-- 用户管理的「重置密码」按钮点种子（#250，ADR 0062）。
--
-- 由 V125 Java 迁移执行：在 /system/sysUser 页面下新增 type=2 按钮，
-- perms=system:user:resetPwd，并显式补授 super_admin。按钮点只在父页面也被授予时生效
-- （ADR 0052/0056），所以不重授页面本身。所有插入都以 NOT EXISTS 守住，
-- 空库与既有库重复执行都不会多出行。

-- ----------------------------
-- 按钮：重置密码（挂在用户管理页面下）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'重置密码',2,'','','system:user:resetPwd','',5,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT id FROM sys_menu WHERE path='/system/sysUser' AND type=1 AND is_deleted=0 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu b WHERE b.perms='system:user:resetPwd' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND m.type=2 AND m.perms='system:user:resetPwd'
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;
