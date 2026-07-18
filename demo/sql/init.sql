CREATE DATABASE IF NOT EXISTS rbac DEFAULT CHARACTER SET utf8mb4;
USE rbac;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 用户表
-- =============================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`          BIGINT      NOT NULL COMMENT '用户ID',
  `username`    VARCHAR(64) NOT NULL COMMENT '登录账号',
  `password`    VARCHAR(100) NOT NULL COMMENT '密码',
  `nickname`    VARCHAR(64) NOT NULL COMMENT '用户昵称',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

INSERT INTO `sys_user` VALUES
(1943123456789012001, 'zhangsan', '123456', '张三', NULL, 'zhangsan@test.com', '13800000001', 1, '2026-07-08 12:03:44', '2026-07-09 22:33:47', 0),
(1943123456789012002, 'lisi',    '123456', '李四', NULL, 'lisi@test.com',    '13800000002', 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
(1943123456789012003, 'wangwu',  '123456', '王五', NULL, 'wangwu@test.com',  '13800000003', 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
(1943123456789012004, 'disabled_user', '123456', '被禁用用户', NULL, 'disabled@test.com', '13800000004', 0, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
(1943123456789012010, 'this_is_a_very_long_username_for_boundary_testing_case_number10', '123456', '边界测试用户', NULL, 'boundary@test.com', '13800000010', 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0);

-- =============================================
-- 2. 角色表
-- =============================================
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT      NOT NULL COMMENT '角色ID',
  `role_name`   VARCHAR(64) NOT NULL COMMENT '角色名称',
  `role_key`    VARCHAR(64) NOT NULL COMMENT '角色权限字符串',
  `sort`        INT         NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

INSERT INTO `sys_role` VALUES
(1001, '管理员',   'admin',  1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(1002, '普通用户', 'common', 2, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0);

-- =============================================
-- 3. 菜单/权限表
-- =============================================
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id`          BIGINT      NOT NULL COMMENT '菜单/权限ID',
  `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父菜单ID',
  `menu_name`   VARCHAR(64) NOT NULL COMMENT '菜单/按钮名称',
  `menu_type`   CHAR(1)     NOT NULL COMMENT '类型：M=目录 C=菜单 F=按钮',
  `path`        VARCHAR(200) DEFAULT NULL COMMENT '前端路由地址',
  `component`   VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
  `perms`       VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
  `icon`        VARCHAR(100) DEFAULT NULL COMMENT '菜单图标',
  `sort`        INT         NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

INSERT INTO `sys_menu` VALUES
(2005, 0,    '首页',     'C', 'home',   'home',         NULL,                  'home',   2, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2001, 0,    '系统管理', 'M', 'system', 'Layout',       NULL,                  'system', 1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2002, 2001, '用户管理', 'C', 'user',   'system/user',  'system:user:list',   'user',   1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2003, 2002, '用户查询', 'F', NULL,     NULL,           'system:user:query',   NULL,     1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2006, 2002, '用户新增', 'F', NULL,     NULL,           'system:user:add',     NULL,     2, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2007, 2002, '用户删除', 'F', NULL,     NULL,           'system:user:delete',  NULL,     3, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2004, 2001, '角色管理', 'C', 'role',   'system/role',  'system:role:list',   'role',   2, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2008, 2004, '角色查询', 'F', NULL,     NULL,           'system:role:query',   NULL,     1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2009, 2004, '角色新增', 'F', NULL,     NULL,           'system:role:add',     NULL,     2, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2010, 2001, '菜单管理', 'C', 'menu',   'system/menu',  'system:menu:list',   'menu',   3, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2011, 2010, '菜单查询', 'F', NULL,     NULL,           'system:menu:query',   NULL,     1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0);

-- =============================================
-- 4. 用户-角色 关联表
-- =============================================
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

INSERT INTO `sys_user_role` VALUES
(1943123456789012001, 1001),
(1943123456789012002, 1002),
(1943123456789012003, 1002);

-- =============================================
-- 5. 角色-菜单 关联表
-- =============================================
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

INSERT INTO `sys_role_menu` VALUES
(1001, 2001), (1001, 2002), (1001, 2003), (1001, 2006), (1001, 2007),
(1001, 2004), (1001, 2008), (1001, 2009),
(1001, 2010), (1001, 2011), (1001, 2005),
(1002, 2001), (1002, 2002), (1002, 2003), (1002, 2006), (1002, 2005);

-- =============================================
-- 6. 操作日志表
-- =============================================
DROP TABLE IF EXISTS `sys_operate_log`;
CREATE TABLE `sys_operate_log` (
  `id`            BIGINT       NOT NULL COMMENT '日志ID',
  `title`         VARCHAR(64)  NOT NULL COMMENT '模块标题',
  `business_type` TINYINT      NOT NULL COMMENT '操作类型：0=其他 1=新增 2=修改 3=删除',
  `method`        VARCHAR(200) NOT NULL COMMENT '调用的Java方法全路径',
  `request_method` VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
  `oper_url`      VARCHAR(200) NOT NULL COMMENT '请求URL',
  `oper_name`     VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名',
  `oper_param`    TEXT         COMMENT '请求参数（JSON）',
  `oper_result`   TEXT         COMMENT '返回结果（JSON）',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '操作状态：0=正常 1=异常',
  `error_msg`     TEXT         COMMENT '异常信息',
  `oper_ip`       VARCHAR(128) DEFAULT NULL COMMENT '操作者IP',
  `oper_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `cost_time`     BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
  PRIMARY KEY (`id`),
  KEY `idx_oper_name` (`oper_name`),
  KEY `idx_oper_time` (`oper_time`),
  KEY `idx_business_type` (`business_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- =============================================
-- 7. 通知日志表
-- =============================================
DROP TABLE IF EXISTS `sys_notify_log`;
CREATE TABLE `sys_notify_log` (
  `id`            BIGINT       NOT NULL COMMENT '通知ID',
  `user_id`       BIGINT       NOT NULL COMMENT '目标用户ID',
  `username`      VARCHAR(64)  DEFAULT NULL COMMENT '用户名',
  `notify_type`   VARCHAR(32)  NOT NULL DEFAULT 'welcome' COMMENT '通知类型',
  `content`       VARCHAR(500) DEFAULT NULL COMMENT '通知内容',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=成功 1=失败',
  `error_msg`     VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知日志表';

SET FOREIGN_KEY_CHECKS = 1;
