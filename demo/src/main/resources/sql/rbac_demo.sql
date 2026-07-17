/*
 RBAC Demo - 完整初始化数据脚本

 说明：本脚本包含 5 张表的建表语句 + 测试数据
 数据库：MySQL 8.0+
 编码：utf8mb4
 主键策略：雪花算法（应用层生成）

 用户-角色-权限分配设计：
 ┌─────────┬──────────┬──────────────────────────────────────────────────┐
 │ 用户    │ 角色     │ 权限范围                                        │
 ├─────────┼──────────┼──────────────────────────────────────────────────┤
 │ 张三    │ 管理员   │ 全部权限（系统管理/用户管理/角色管理/菜单管理）    │
 │ 李四    │ 普通用户 │ 用户查看/新增，系统管理目录                        │
 │ 王五    │ 普通用户 │ 用户查看/新增，系统管理目录                        │
 │ 其余    │ 无角色   │ 无权限（token 中 permissions 为空）              │
 └─────────┴──────────┴──────────────────────────────────────────────────┘

 菜单树结构：
 首页 (C)
 系统管理 (M)
   ├── 用户管理 (C)
   │    ├── 用户查询 (F)
   │    ├── 用户新增 (F)
   │    └── 用户删除 (F)
   ├── 角色管理 (C)
   │    ├── 角色查询 (F)
   │    └── 角色新增 (F)
   └── 菜单管理 (C)
        └── 菜单查询 (F)
*/
CREATE DATABASE rbac;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 用户表
-- =============================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`          BIGINT      NOT NULL COMMENT '用户ID',
  `username`    VARCHAR(64) NOT NULL COMMENT '登录账号',
  `password`    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密后存储，阶段4接入）',
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
(1943123456789012001, 'zhangsan', '123456', '张三', 'https://cdn.test.com/avatar/1.png', 'zhangsan@test.com', '13800000001', 1, '2026-07-08 12:03:44', '2026-07-09 22:33:47', 0),
(1943123456789012002, 'lisi',    '123456', '李四', 'https://cdn.test.com/avatar/2.png', 'lisi@test.com',    '13800000002', 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
(1943123456789012003, 'wangwu',  '123456', '王五', NULL, 'wangwu@test.com',  '13800000003', 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
(1943123456789012004, 'disabled_user', '123456', '被禁用用户', NULL, 'disabled@test.com', '13800000004', 0, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
(1943123456789012005, 'deleted_user',  '123456', '已删除用户', NULL, 'deleted@test.com',  '13800000005', 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 1),
(1943123456789012006, 'test''or1=1--',  '123456', '注入测试', NULL, 'sqltest@test.com',  '13800000006', 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
(1943123456789012007, 'no_email_user',  '123456', '无邮箱用户', NULL, NULL, '13800000007', 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
(1943123456789012008, 'no_phone_user',  '123456', '无手机号用户', NULL, 'nophone@test.com', NULL, 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
(1943123456789012009, 'emoji_user',     '123456', '快乐小天使😊🎉', NULL, 'emoji@test.com', '13800000009', 1, '2026-07-08 12:03:44', '2026-07-08 12:03:44', 0),
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
-- 3. 菜单/权限表（自引用树形结构）
--    M=目录  C=菜单  F=按钮
-- =============================================
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id`          BIGINT      NOT NULL COMMENT '菜单/权限ID',
  `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父菜单ID，0表示顶级',
  `menu_name`   VARCHAR(64) NOT NULL COMMENT '菜单/按钮名称',
  `menu_type`   CHAR(1)     NOT NULL COMMENT '类型：M=目录 C=菜单 F=按钮',
  `path`        VARCHAR(200) DEFAULT NULL COMMENT '前端路由地址',
  `component`   VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
  `perms`       VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 system:user:add',
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
-- 首页
(2005, 0,    '首页',     'C', 'home',   'home',         NULL,                  'home',   2, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
-- 系统管理（目录）
(2001, 0,    '系统管理', 'M', 'system', 'Layout',       NULL,                  'system', 1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
-- 用户管理（菜单）
(2002, 2001, '用户管理', 'C', 'user',   'system/user',  'system:user:list',   'user',   1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
-- 用户管理按钮
(2003, 2002, '用户查询', 'F', NULL,     NULL,           'system:user:query',   NULL,     1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2006, 2002, '用户新增', 'F', NULL,     NULL,           'system:user:add',     NULL,     2, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2007, 2002, '用户删除', 'F', NULL,     NULL,           'system:user:delete',  NULL,     3, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
-- 角色管理（菜单）
(2004, 2001, '角色管理', 'C', 'role',   'system/role',  'system:role:list',   'role',   2, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
-- 角色管理按钮
(2008, 2004, '角色查询', 'F', NULL,     NULL,           'system:role:query',   NULL,     1, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
(2009, 2004, '角色新增', 'F', NULL,     NULL,           'system:role:add',     NULL,     2, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
-- 菜单管理（菜单）
(2010, 2001, '菜单管理', 'C', 'menu',   'system/menu',  'system:menu:list',   'menu',   3, 1, '2026-07-08 14:11:46', '2026-07-08 14:11:46', 0),
-- 菜单管理按钮
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
(1943123456789012001, 1001),   -- 张三 → 管理员
(1943123456789012002, 1002),   -- 李四 → 普通用户
(1943123456789012003, 1002);   -- 王五 → 普通用户

-- =============================================
-- 5. 角色-菜单 关联表
-- =============================================
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- 管理员：全部菜单权限
INSERT INTO `sys_role_menu` VALUES
(1001, 2001),  -- 系统管理（目录）
(1001, 2002),  -- 用户管理（菜单）
(1001, 2003),  -- 用户查询
(1001, 2006),  -- 用户新增
(1001, 2007),  -- 用户删除
(1001, 2004),  -- 角色管理（菜单）
(1001, 2008),  -- 角色查询
(1001, 2009),  -- 角色新增
(1001, 2010),  -- 菜单管理（菜单）
(1001, 2011),  -- 菜单查询
(1001, 2005);  -- 首页

-- 普通用户：系统管理目录 + 用户管理（查看/新增）+ 首页
INSERT INTO `sys_role_menu` VALUES
(1002, 2001),  -- 系统管理（目录）
(1002, 2002),  -- 用户管理（菜单）
(1002, 2003),  -- 用户查询
(1002, 2006),  -- 用户新增
(1002, 2005);  -- 首页

SET FOREIGN_KEY_CHECKS = 1;
