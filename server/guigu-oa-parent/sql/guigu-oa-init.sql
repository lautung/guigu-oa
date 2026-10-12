-- 云尚办公 业务表初始化脚本（开发环境）
-- 说明：Flowable 的 ACT_* / FLW_* 表由引擎在应用启动时自动创建（application-dev.yml: database-schema-update: true），
--      本脚本只负责业务表（sys_*、oa_*）与最小种子数据。
--      从 Activiti 7 切到 Flowable 8 见同目录 flowable8-cutover.sh / flowable8-cutover.md。
-- 使用：mysql -uroot -proot guigu-oa < server/guigu-oa-parent/sql/guigu-oa-init.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 系统：用户
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(20) DEFAULT NULL COMMENT '用户名',
  `password` VARCHAR(100) DEFAULT NULL COMMENT '密码(BCrypt)',
  `name` VARCHAR(20) DEFAULT NULL COMMENT '姓名',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机',
  `head_url` VARCHAR(200) DEFAULT NULL COMMENT '头像地址',
  `dept_id` BIGINT DEFAULT NULL COMMENT '部门id',
  `post_id` BIGINT DEFAULT NULL COMMENT '岗位id',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `open_id` VARCHAR(100) DEFAULT NULL COMMENT '微信openId(历史字段)',
  `status` TINYINT DEFAULT '1' COMMENT '状态(1:正常 0:停用)',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT '0' COMMENT '逻辑删除(1:已删除 0:未删除)',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 系统：角色
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_name` VARCHAR(20) DEFAULT NULL COMMENT '角色名称',
  `role_code` VARCHAR(20) DEFAULT NULL COMMENT '角色编码',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ----------------------------
-- 系统：菜单
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` BIGINT DEFAULT '0' COMMENT '所属上级',
  `name` VARCHAR(50) DEFAULT NULL COMMENT '名称',
  `type` TINYINT DEFAULT NULL COMMENT '类型(1:菜单 2:按钮)',
  `path` VARCHAR(200) DEFAULT NULL COMMENT '路由地址',
  `component` VARCHAR(200) DEFAULT NULL COMMENT '组件路径',
  `perms` VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
  `icon` VARCHAR(100) DEFAULT NULL COMMENT '图标',
  `sort_value` INT DEFAULT NULL COMMENT '排序',
  `status` TINYINT DEFAULT '1' COMMENT '状态(0:禁止 1:正常)',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- ----------------------------
-- 系统：角色-菜单
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` BIGINT DEFAULT NULL COMMENT '角色id',
  `menu_id` BIGINT DEFAULT NULL COMMENT '菜单id',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单表';

-- ----------------------------
-- 系统：用户-角色
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` BIGINT DEFAULT NULL COMMENT '角色id',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户id',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色表';

-- ----------------------------
-- 审批：类型
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oa_process_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(50) DEFAULT NULL COMMENT '类型名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批类型表';

-- ----------------------------
-- 审批：模板
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oa_process_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(50) DEFAULT NULL COMMENT '模板名称',
  `icon_url` VARCHAR(200) DEFAULT NULL COMMENT '图标路径',
  `process_type_id` BIGINT DEFAULT NULL COMMENT '审批类型id',
  `form_props` TEXT COMMENT '表单属性',
  `form_options` TEXT COMMENT '表单选项',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `process_definition_key` VARCHAR(100) DEFAULT NULL COMMENT '流程定义key',
  `process_definition_path` VARCHAR(200) DEFAULT NULL COMMENT '流程定义上传路径',
  `process_model_id` VARCHAR(100) DEFAULT NULL COMMENT '流程定义模型id',
  `status` TINYINT DEFAULT '0' COMMENT '状态(0:未发布 1:已发布)',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批模板表';

-- ----------------------------
-- 审批：实例
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oa_process` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `process_code` VARCHAR(50) DEFAULT NULL COMMENT '审批code',
  `user_id` BIGINT DEFAULT NULL COMMENT '发起用户id',
  `process_template_id` BIGINT DEFAULT NULL COMMENT '审批模板id',
  `process_type_id` BIGINT DEFAULT NULL COMMENT '审批类型id',
  `title` VARCHAR(200) DEFAULT NULL COMMENT '标题',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `form_values` TEXT COMMENT '表单值',
  `process_instance_id` VARCHAR(100) DEFAULT NULL COMMENT '流程实例id',
  `current_auditor` VARCHAR(200) DEFAULT NULL COMMENT '当前审批人',
  `status` TINYINT DEFAULT '0' COMMENT '状态(0:默认 1:审批中 2:审批通过 -1:驳回)',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例表';

-- ----------------------------
-- 审批：流转记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `oa_process_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `process_id` BIGINT DEFAULT NULL COMMENT '审批流程id',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '审批描述',
  `status` TINYINT DEFAULT NULL COMMENT '状态',
  `operate_user_id` BIGINT DEFAULT NULL COMMENT '操作用户id',
  `operate_user` VARCHAR(200) DEFAULT NULL COMMENT '操作用户',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流转记录表';

-- ----------------------------
-- 种子数据：超级管理员（id=1，登录后自动拥有全部配置菜单权限）
-- 密码为 BCrypt('111111')，与登录口令 111111 对应
-- ----------------------------
INSERT INTO `sys_user` (`id`,`username`,`password`,`name`,`phone`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT 1,'admin','$2b$10$JF/wulA6oTURVNZYCgyNiuJFpnogF58MsYwPpO8dL/EWDQI4TuiGa','管理员','13800000000',1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username`='admin');

INSERT INTO `sys_user` (`username`,`password`,`name`,`phone`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT 'user','$2b$10$JF/wulA6oTURVNZYCgyNiuJFpnogF58MsYwPpO8dL/EWDQI4TuiGa','办事用户','13800000001',1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username`='user');

-- ----------------------------
-- 种子数据：配置类 sys_menu（userId=1 登录后自动拥有全部配置菜单）
-- 不包含公众号 / Dashboard / 部门岗位日志 / 在线流程设计器
-- ----------------------------
INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT 0,'组织与权限',0,'/system','Layout','','el-icon-user-solid',1,1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `path`='/system' AND `parent_id`=0 AND `is_deleted`=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'用户管理',1,'sysUser','system/sysUser/list','','',1,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='/system' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.path='sysUser' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'角色管理',1,'sysRole','system/sysRole/list','','',2,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='/system' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.path='sysRole' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'菜单管理',1,'sysMenu','system/sysMenu/list','','',3,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='/system' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.path='sysMenu' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'菜单授权',1,'assignAuth','system/sysRole/assignAuth','','',4,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='sysRole' AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.path='assignAuth' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'角色列表',2,'','','bnt.sysRole.list','',1,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='sysRole' AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.perms='bnt.sysRole.list' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'角色新增',2,'','','bnt.sysRole.add','',2,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='sysRole' AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.perms='bnt.sysRole.add' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'角色修改',2,'','','bnt.sysRole.update','',3,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='sysRole' AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.perms='bnt.sysRole.update' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'角色删除',2,'','','bnt.sysRole.remove','',4,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='sysRole' AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.perms='bnt.sysRole.remove' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT 0,'审批设置',0,'/processSet','Layout','','el-icon-s-tools',2,1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `path`='/processSet' AND `parent_id`=0 AND `is_deleted`=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'审批类型',1,'processType','processSet/processType/list','','',1,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='/processSet' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.path='processType' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'审批模板',1,'processTemplate','processSet/processTemplate/list','','',2,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='/processSet' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.path='processTemplate' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'审批模板设置',1,'templateSet','processSet/processTemplate/templateSet','','',3,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='processTemplate' AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.path='templateSet' AND c.is_deleted=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT 0,'审批查询',0,'/processMgr','Layout','','el-icon-s-order',3,1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `path`='/processMgr' AND `parent_id`=0 AND `is_deleted`=0);

INSERT INTO `sys_menu` (`parent_id`,`name`,`type`,`path`,`component`,`perms`,`icon`,`sort_value`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT p.id,'审批实例查询',1,'process','processMgr/process/list','','',1,1,NOW(),NOW(),0
FROM `sys_menu` p
WHERE p.path='/processMgr' AND p.parent_id=0 AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.parent_id=p.id AND c.path='process' AND c.is_deleted=0);

-- ----------------------------
-- 种子数据：审批类型与审批模板（formProps / formOptions 为 form-create JSON，供设计器回填）
-- ----------------------------
INSERT INTO `oa_process_type` (`name`,`description`,`create_time`,`update_time`,`is_deleted`)
SELECT '请假','员工请假',NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM `oa_process_type` WHERE `name`='请假' AND `is_deleted`=0);

INSERT INTO `oa_process_template` (
  `name`,`icon_url`,`process_type_id`,`form_props`,`form_options`,`description`,
  `process_definition_key`,`process_definition_path`,`status`,`create_time`,`update_time`,`is_deleted`
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
FROM `oa_process_type` t
WHERE t.name='请假' AND t.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `oa_process_template` p WHERE p.name='请假申请' AND p.is_deleted=0);

INSERT INTO `oa_process_template` (
  `name`,`icon_url`,`process_type_id`,`form_props`,`form_options`,`description`,
  `process_definition_key`,`process_definition_path`,`status`,`create_time`,`update_time`,`is_deleted`
)
SELECT
  '病假草稿',
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
FROM `oa_process_type` t
WHERE t.name='请假' AND t.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM `oa_process_template` p WHERE p.name='病假草稿' AND p.is_deleted=0);

SET FOREIGN_KEY_CHECKS = 1;
