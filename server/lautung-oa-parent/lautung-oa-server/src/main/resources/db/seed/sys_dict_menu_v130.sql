-- 字典管理菜单与首批四组字典种子（#251，ADR 0062）。
--
-- 由 V130 Java 迁移执行：在 /system 目录下新增「字典管理」页面行与
-- system:dict:add / system:dict:edit / system:dict:remove 三个按钮点，显式补授 super_admin，
-- 并种入首批四组字典：公告类型、公告状态、定时任务状态、登录日志状态。
-- 值以现有代码常量为准：Notice.TYPE_NOTIFICATION/ANNOUNCEMENT、Notice.STATUS_*、
-- SysJobLog.STATUS_*、LoginEventType 的 status。
-- 页面路径是绝对路径（V111 规则）；按钮行 path/component 为空，perms 必填且唯一。
-- 所有插入都以 NOT EXISTS 守住，空库与既有库重复执行都不会多出行。

-- ----------------------------
-- 页面：/system/dict（组件 system/dict/index）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'字典管理',1,'/system/dict','system/dict/index','','',7,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system' AND p.parent_id=0 AND p.type=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c
                  WHERE c.path='/system/dict' AND c.type=1 AND c.is_deleted=0);

-- ----------------------------
-- 按钮点：system:dict:add（新增，类型与数据共用）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'新增字典',2,'','','system:dict:add','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/dict' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:dict:add' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：system:dict:edit（修改，刷新缓存复用该点）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'修改字典',2,'','','system:dict:edit','',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/dict' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:dict:edit' AND b.is_deleted=0);

-- ----------------------------
-- 按钮点：system:dict:remove（删除）
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'删除字典',2,'','','system:dict:remove','',3,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_menu p
WHERE p.path='/system/dict' AND p.type=1 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu b
                  WHERE b.perms='system:dict:remove' AND b.is_deleted=0);

-- ----------------------------
-- 显式补授 super_admin：只授本次新增的页面/按钮，不重授全量。
-- ----------------------------
INSERT INTO sys_role_menu (role_id,menu_id,create_time,update_time,is_deleted)
SELECT r.id,m.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM sys_role r
JOIN sys_menu m ON m.is_deleted=0 AND (
       (m.type=1 AND m.path='/system/dict')
    OR (m.type=2 AND m.perms IN ('system:dict:add','system:dict:edit','system:dict:remove'))
)
LEFT JOIN sys_role_menu existing
       ON existing.role_id=r.id
      AND existing.menu_id=m.id
      AND existing.is_deleted=0
WHERE r.role_code='super_admin'
  AND r.is_deleted=0
  AND existing.id IS NULL;

-- ----------------------------
-- 字典类型：公告类型 / 公告状态 / 定时任务状态 / 登录日志状态
-- ----------------------------
INSERT INTO sys_dict_type (dict_name,dict_type,status,remark,create_time,update_time,is_deleted)
SELECT '公告类型','sys_notice_type',1,'通知 1 / 公告 2（Notice.TYPE_*）',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='sys_notice_type');

INSERT INTO sys_dict_type (dict_name,dict_type,status,remark,create_time,update_time,is_deleted)
SELECT '公告状态','sys_notice_status',1,'草稿 0 / 已发布 1 / 已撤回 2（Notice.STATUS_*）',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='sys_notice_status');

INSERT INTO sys_dict_type (dict_name,dict_type,status,remark,create_time,update_time,is_deleted)
SELECT '定时任务状态','sys_job_status',1,'成功 1 / 失败 2 / 跳过 3（SysJobLog.STATUS_*）',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='sys_job_status');

INSERT INTO sys_dict_type (dict_name,dict_type,status,remark,create_time,update_time,is_deleted)
SELECT '登录日志状态','sys_login_status',1,'成功 0 / 失败 1 / 锁定 2 / 解锁 3（LoginEventType）',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='sys_login_status');

-- ----------------------------
-- 公告类型：1 通知 / 2 公告
-- ----------------------------
INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_notice_type','通知','1',1,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_notice_type' AND dict_value='1');

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_notice_type','公告','2',2,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_notice_type' AND dict_value='2');

-- ----------------------------
-- 公告状态：0 草稿 / 1 已发布 / 2 已撤回
-- ----------------------------
INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_notice_status','草稿','0',1,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_notice_status' AND dict_value='0');

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_notice_status','已发布','1',2,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_notice_status' AND dict_value='1');

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_notice_status','已撤回','2',3,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_notice_status' AND dict_value='2');

-- ----------------------------
-- 定时任务状态：1 成功 / 2 失败 / 3 跳过
-- ----------------------------
INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_job_status','成功','1',1,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_job_status' AND dict_value='1');

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_job_status','失败','2',2,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_job_status' AND dict_value='2');

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_job_status','跳过','3',3,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_job_status' AND dict_value='3');

-- ----------------------------
-- 登录日志状态：0 成功 / 1 失败 / 2 锁定 / 3 解锁
-- ----------------------------
INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_login_status','成功','0',1,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_login_status' AND dict_value='0');

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_login_status','失败','1',2,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_login_status' AND dict_value='1');

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_login_status','锁定','2',3,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_login_status' AND dict_value='2');

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,sort_value,status,remark,create_time,update_time,is_deleted)
SELECT 'sys_login_status','解锁','3',4,1,'',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                  WHERE dict_type='sys_login_status' AND dict_value='3');
