-- 配置管理员种子角色：通过菜单授予获得配置权限，取代 userId=1 的全菜单分支。
-- 幂等：已有同 role_code 的角色、已有菜单授予或用户角色时不重复插入。

INSERT INTO sys_role (role_name, role_code, description, create_time, update_time, is_deleted)
SELECT '配置管理员', 'config_admin', '种子账号 admin 的配置菜单授予', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM (SELECT 1 AS one) seed
LEFT JOIN sys_role existing ON existing.role_code = 'config_admin' AND existing.is_deleted = 0
WHERE existing.id IS NULL;

INSERT INTO sys_role_menu (role_id, menu_id, create_time, update_time, is_deleted)
WITH RECURSIVE config_menu(id) AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id = 0
      AND path IN ('/system', '/processSet', '/processMgr')
      AND is_deleted = 0
    UNION ALL
    SELECT child.id
    FROM sys_menu child
    JOIN config_menu parent ON child.parent_id = parent.id
    WHERE child.is_deleted = 0
)
SELECT r.id, m.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_role r
JOIN config_menu m ON 1 = 1
LEFT JOIN sys_role_menu existing
       ON existing.role_id = r.id
      AND existing.menu_id = m.id
      AND existing.is_deleted = 0
WHERE r.role_code = 'config_admin'
  AND r.is_deleted = 0
  AND existing.id IS NULL;

INSERT INTO sys_user_role (user_id, role_id, create_time, update_time, is_deleted)
SELECT u.id, r.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_user u
JOIN sys_role r ON r.role_code = 'config_admin' AND r.is_deleted = 0
LEFT JOIN sys_user_role existing
       ON existing.user_id = u.id
      AND existing.role_id = r.id
      AND existing.is_deleted = 0
WHERE u.username = 'admin'
  AND u.is_deleted = 0
  AND existing.id IS NULL;
