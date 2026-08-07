-- 初始化默认管理员账号
-- 用户名: admin     密码: admin123  (BCrypt加密)
-- 用户名: admin2    密码: admin123  (BCrypt加密)

INSERT INTO admin_users (id, username, password, nickname, role, status, created_at, updated_at) VALUES
('1', 'admin',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '超级管理员', 'super_admin', 1, NOW(), NOW()),
('2', 'admin2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '管理员',     'admin',       1, NOW(), NOW())
ON DUPLICATE KEY UPDATE username = VALUES(username);
