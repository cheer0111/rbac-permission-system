-- 恢复 sys_user + sys_user_role 数据
-- 所有用户密码统一为 123456 的 BCrypt 哈希（由 Python bcrypt 算法生成）
-- 原始数据已备份至 sys_user_backup.json

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM sys_user_role;
DELETE FROM sys_user;

INSERT INTO sys_user (id, username, password, nickname, avatar, email, phone, status, create_time, update_time, del_flag) VALUES
(1943123456789012001, 'zhangsan',    '$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '张三',       'https://cdn.test.com/avatar/1.png', 'zhangsan@test.com',  '13800000001', 1, '2026-07-08 12:03:44', '2026-07-17 14:48:29', 0),
(1943123456789012002, 'lisi',        '$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '李四',       'https://cdn.test.com/avatar/2.png', 'lisi@test.com',     '13800000002', 1, '2026-07-08 12:03:44', '2026-07-17 14:48:29', 0),
(1943123456789012003, 'wangwu',      '$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '王五',       NULL,                            'wangwu@test.com',  '13800000003', 1, '2026-07-08 12:03:44', '2026-07-17 14:48:29', 0),
(1943123456789012004, 'disabled_user','$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '被禁用用户', NULL,                            'disabled@test.com', '13800000004', 0, '2026-07-08 12:03:44', '2026-07-17 14:48:29', 0),
(1943123456789012005, 'deleted_user', '$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '已删除用户', NULL,                            'deleted@test.com',  '13800000005', 1, '2026-07-08 12:03:44', '2026-07-17 14:38:07', 1),
(1943123456789012006, 'test''or1=1--', '$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '注入测试',   NULL,                            'sqltest@test.com',  '13800000006', 1, '2026-07-08 12:03:44', '2026-07-17 14:48:29', 0),
(1943123456789012007, 'no_email_user','$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '无邮箱用户', NULL,                            NULL,               '13800000007', 1, '2026-07-08 12:03:44', '2026-07-17 14:48:29', 0),
(1943123456789012008, 'no_phone_user','$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '无手机号用户',NULL,                            'nophone@test.com', NULL,              1, '2026-07-08 12:03:44', '2026-07-17 14:48:29', 0),
(1943123456789012009, 'emoji_user',   '$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '快乐小天使', NULL,                            'emoji@test.com',   '13800000009', 1, '2026-07-08 12:03:44', '2026-07-17 14:48:29', 0),
(1943123456789012010, 'this_is_a_very_long_username_for_boundary_testing_case_number10', '$2b$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me', '边界测试用户', NULL, 'boundary@test.com', '13800000010', 1, '2026-07-08 12:03:44', '2026-07-17 14:48:29', 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1943123456789012001, 1001),
(1943123456789012002, 1002),
(1943123456789012003, 1002);

SET FOREIGN_KEY_CHECKS = 1;

SELECT '恢复完成' AS result;
