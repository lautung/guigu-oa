-- 后台页面路径绝对化（ADR 0056，#222）：认证三页与隐藏页从相对段改为绝对路径，
-- 与 #221 已迁移的审批页同一规则。按钮点 path 为空，不受影响。
-- 每条 UPDATE 以旧值定位，重复执行不会再命中。

UPDATE sys_menu
SET path = '/system/sysUser'
WHERE path = 'sysUser'
  AND type = 1
  AND is_deleted = 0;

UPDATE sys_menu
SET path = '/system/sysRole'
WHERE path = 'sysRole'
  AND type = 1
  AND is_deleted = 0;

UPDATE sys_menu
SET path = '/system/sysMenu'
WHERE path = 'sysMenu'
  AND type = 1
  AND is_deleted = 0;

UPDATE sys_menu
SET path = '/system/assignAuth'
WHERE path = 'assignAuth'
  AND type = 1
  AND is_deleted = 0;
