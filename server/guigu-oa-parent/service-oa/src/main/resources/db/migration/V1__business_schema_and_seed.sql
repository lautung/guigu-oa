-- 云尚办公业务表与基础种子。Flyway 管理 sys_* / oa_*；ACT_* / FLW_* 仍由 Flowable 创建。
-- 密码哈希暂用 MD5；序 3 的鉴权迁移将转换为 BCrypt。

-- ----------------------------
-- 系统：用户
-- ----------------------------
CREATE TABLE sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(20) DEFAULT NULL,
  password VARCHAR(100) DEFAULT NULL,
  name VARCHAR(20) DEFAULT NULL,
  phone VARCHAR(20) DEFAULT NULL,
  head_url VARCHAR(200) DEFAULT NULL,
  dept_id BIGINT DEFAULT NULL,
  post_id BIGINT DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL,
  open_id VARCHAR(100) DEFAULT NULL,
  status TINYINT DEFAULT '1',
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

CREATE INDEX idx_username ON sys_user(username);

-- ----------------------------
-- 系统：角色
-- ----------------------------
CREATE TABLE sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  role_name VARCHAR(20) DEFAULT NULL,
  role_code VARCHAR(20) DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

-- ----------------------------
-- 系统：菜单
-- ----------------------------
CREATE TABLE sys_menu (
  id BIGINT NOT NULL AUTO_INCREMENT,
  parent_id BIGINT DEFAULT '0',
  name VARCHAR(50) DEFAULT NULL,
  type TINYINT DEFAULT NULL,
  path VARCHAR(200) DEFAULT NULL,
  component VARCHAR(200) DEFAULT NULL,
  perms VARCHAR(100) DEFAULT NULL,
  icon VARCHAR(100) DEFAULT NULL,
  sort_value INT DEFAULT NULL,
  status TINYINT DEFAULT '1',
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

-- ----------------------------
-- 系统：角色-菜单
-- ----------------------------
CREATE TABLE sys_role_menu (
  id BIGINT NOT NULL AUTO_INCREMENT,
  role_id BIGINT DEFAULT NULL,
  menu_id BIGINT DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

-- ----------------------------
-- 系统：用户-角色
-- ----------------------------
CREATE TABLE sys_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  role_id BIGINT DEFAULT NULL,
  user_id BIGINT DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

-- ----------------------------
-- 审批：类型
-- ----------------------------
CREATE TABLE oa_process_type (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(50) DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

-- ----------------------------
-- 审批：模板
-- ----------------------------
CREATE TABLE oa_process_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(50) DEFAULT NULL,
  icon_url VARCHAR(200) DEFAULT NULL,
  process_type_id BIGINT DEFAULT NULL,
  form_props TEXT,
  form_options TEXT,
  description VARCHAR(255) DEFAULT NULL,
  process_definition_key VARCHAR(100) DEFAULT NULL,
  process_definition_path VARCHAR(200) DEFAULT NULL,
  process_model_id VARCHAR(100) DEFAULT NULL,
  status TINYINT DEFAULT '0',
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

-- ----------------------------
-- 审批：实例
-- ----------------------------
CREATE TABLE oa_process (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_code VARCHAR(50) DEFAULT NULL,
  user_id BIGINT DEFAULT NULL,
  process_template_id BIGINT DEFAULT NULL,
  process_type_id BIGINT DEFAULT NULL,
  title VARCHAR(200) DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL,
  form_values TEXT,
  process_instance_id VARCHAR(100) DEFAULT NULL,
  current_auditor VARCHAR(200) DEFAULT NULL,
  status TINYINT DEFAULT '0',
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

-- ----------------------------
-- 审批：流转记录
-- ----------------------------
CREATE TABLE oa_process_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_id BIGINT DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL,
  status TINYINT DEFAULT NULL,
  operate_user_id BIGINT DEFAULT NULL,
  operate_user VARCHAR(200) DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

-- ----------------------------
-- 种子数据：基础登录账号
-- 密码为 MD5('111111') = 96e79218965eb72c92a549dd5a330112
-- ----------------------------
INSERT INTO sys_user (username,password,name,phone,status,create_time,update_time,is_deleted)
SELECT 'admin','96e79218965eb72c92a549dd5a330112','管理员','13800000000',1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='admin');

INSERT INTO sys_user (username,password,name,phone,status,create_time,update_time,is_deleted)
SELECT 'user','96e79218965eb72c92a549dd5a330112','办事用户','13800000001',1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='user');

-- ----------------------------
-- 种子数据：系统设置、审批设置与审批查询菜单
-- 不包含公众号 / Dashboard / 部门岗位日志 / 在线流程设计器
-- ----------------------------
INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT 0,'组织与权限',0,'/system','Layout','','el-icon-user-solid',1,1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system' AND parent_id=0 AND is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'用户管理',1,'sysUser','system/sysUser/list','','',1,1,NOW(),NOW(),0
FROM sys_menu p
WHERE p.path='/system' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.parent_id=p.id AND c.path='sysUser' AND c.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'角色管理',1,'sysRole','system/sysRole/list','','',2,1,NOW(),NOW(),0
FROM sys_menu p
WHERE p.path='/system' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.parent_id=p.id AND c.path='sysRole' AND c.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'菜单管理',1,'sysMenu','system/sysMenu/list','','',3,1,NOW(),NOW(),0
FROM sys_menu p
WHERE p.path='/system' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.parent_id=p.id AND c.path='sysMenu' AND c.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'菜单授权',1,'assignAuth','system/sysRole/assignAuth','','',4,1,NOW(),NOW(),0
FROM sys_menu p
WHERE p.path='sysRole' AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.parent_id=p.id AND c.path='assignAuth' AND c.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT 0,'审批设置',0,'/processSet','Layout','','el-icon-s-tools',2,1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/processSet' AND parent_id=0 AND is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'审批类型',1,'processType','processSet/processType/list','','',1,1,NOW(),NOW(),0
FROM sys_menu p
WHERE p.path='/processSet' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.parent_id=p.id AND c.path='processType' AND c.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'审批模板',1,'processTemplate','processSet/processTemplate/list','','',2,1,NOW(),NOW(),0
FROM sys_menu p
WHERE p.path='/processSet' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.parent_id=p.id AND c.path='processTemplate' AND c.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'审批模板设置',1,'templateSet','processSet/processTemplate/templateSet','','',3,1,NOW(),NOW(),0
FROM sys_menu p
WHERE p.path='processTemplate' AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.parent_id=p.id AND c.path='templateSet' AND c.is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT 0,'审批查询',0,'/processMgr','Layout','','el-icon-s-order',3,1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/processMgr' AND parent_id=0 AND is_deleted=0);

INSERT INTO sys_menu (parent_id,name,type,path,component,perms,icon,sort_value,status,create_time,update_time,is_deleted)
SELECT p.id,'审批实例查询',1,'process','processMgr/process/list','','',1,1,NOW(),NOW(),0
FROM sys_menu p
WHERE p.path='/processMgr' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.parent_id=p.id AND c.path='process' AND c.is_deleted=0);

-- ----------------------------
-- 种子数据：审批类型与审批模板（formProps / formOptions 为 form-create JSON，供设计器回填）
-- ----------------------------
INSERT INTO oa_process_type (name,description,create_time,update_time,is_deleted)
SELECT '请假','员工请假',NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM oa_process_type WHERE name='请假' AND is_deleted=0);

INSERT INTO oa_process_template (
  name,icon_url,process_type_id,form_props,form_options,description,
  process_definition_key,process_definition_path,status,create_time,update_time,is_deleted
)
SELECT
  '请假申请',
  'https://gw.alicdn.com/tfs/TB1t695CFYqK1RjSZLeXXbXppXa-102-102.png',
  t.id,
  '[{"type":"input","field":"reason","title":"请假原因","$required":true,"props":{"placeholder":"请输入请假原因"}},{"type":"input","field":"days","title":"请假天数","$required":true,"props":{"type":"number","placeholder":"请输入请假天数"}}]',
  '{"submitBtn":false,"form":{"labelPosition":"right","labelWidth":"125px"}}',
  '员工请假',
  'qingjia',
  'processes/qingjia.zip',
  1,
  NOW(),
  NOW(),
  0
FROM oa_process_type t
WHERE t.name='请假' AND t.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM oa_process_template p WHERE p.name='请假申请' AND p.is_deleted=0);

INSERT INTO oa_process_template (
  name,icon_url,process_type_id,form_props,form_options,description,
  process_definition_key,process_definition_path,status,create_time,update_time,is_deleted
)
SELECT
  '未发布病假',
  'https://gw.alicdn.com/tfs/TB1t695CFYqK1RjSZLeXXbXppXa-102-102.png',
  t.id,
  '[{"type":"input","field":"reason","title":"请假原因","$required":true,"props":{"placeholder":"请输入请假原因"}},{"type":"input","field":"days","title":"请假天数","$required":true,"props":{"type":"number","placeholder":"请输入请假天数"}}]',
  '{"submitBtn":false,"form":{"labelPosition":"right","labelWidth":"125px"}}',
  '未发布的病假模板',
  '',
  '',
  0,
  NOW(),
  NOW(),
  0
FROM oa_process_type t
WHERE t.name='请假' AND t.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM oa_process_template p WHERE p.name='未发布病假' AND p.is_deleted=0);
