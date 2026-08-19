-- 云尚办公 业务表初始化脚本（开发环境）
-- 说明：Activiti 的 ACT_* 表由引擎在应用启动时自动创建（application-dev.yml: database-schema-update: true），
--      本脚本只负责业务表（sys_*、oa_*）与最小种子数据。
-- 使用：mysql -uroot -proot guigu-oa < 后端代码/guigu-oa-parent/sql/guigu-oa-init.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 系统：用户
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(20) DEFAULT NULL COMMENT '用户名',
  `password` VARCHAR(100) DEFAULT NULL COMMENT '密码(MD5)',
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
-- 密码为 MD5('111111') = 96e79218965eb72c92a549dd5a330112
-- ----------------------------
INSERT INTO `sys_user` (`id`,`username`,`password`,`name`,`phone`,`status`,`create_time`,`update_time`,`is_deleted`)
SELECT 1,'admin','96e79218965eb72c92a549dd5a330112','管理员','13800000000',1,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username`='admin');

SET FOREIGN_KEY_CHECKS = 1;
