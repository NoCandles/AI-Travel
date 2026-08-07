-- ============================================
-- 拾路派管理后台 — 完整数据库初始化/修复
-- 执行日期：2026-06-30
-- ============================================

-- 1. 管理员账号表
CREATE TABLE IF NOT EXISTS `admin_users` (
    `id`          VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`    VARCHAR(200) NOT NULL COMMENT 'BCrypt加密密码',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '显示名称',
    `role`        VARCHAR(20)  DEFAULT 'admin' COMMENT 'admin=普通管理员, super_admin=超级管理员',
    `status`      TINYINT      DEFAULT 1 COMMENT '1=正常, 0=禁用',
    `last_login`  DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号表';

-- 2. 管理员操作日志表
CREATE TABLE IF NOT EXISTS `admin_logs` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `admin_id`    VARCHAR(36)  DEFAULT NULL COMMENT '操作管理员ID',
    `admin_name`  VARCHAR(50)  DEFAULT NULL COMMENT '操作管理员名',
    `module`      VARCHAR(50)  DEFAULT NULL COMMENT '操作模块',
    `action`      VARCHAR(50)  DEFAULT NULL COMMENT '操作动作',
    `target_type` VARCHAR(50)  DEFAULT NULL COMMENT '目标类型',
    `target_id`   VARCHAR(100) DEFAULT NULL COMMENT '目标ID',
    `detail`      TEXT         DEFAULT NULL COMMENT 'JSON格式操作详情',
    `ip`          VARCHAR(45)  DEFAULT NULL COMMENT '操作IP',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_module` (`module`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志表';

-- 3. 敏感词库表
CREATE TABLE IF NOT EXISTS `sensitive_words` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `word`       VARCHAR(100) NOT NULL COMMENT '敏感词',
    `level`      TINYINT      DEFAULT 1 COMMENT '1=普通, 2=严重',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库';

-- 4. 系统公告表
CREATE TABLE IF NOT EXISTS `system_announcements` (
    `id`          VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `title`       VARCHAR(200) NOT NULL COMMENT '公告标题',
    `content`     TEXT         NOT NULL COMMENT '公告内容',
    `level`       VARCHAR(20)  DEFAULT 'info' COMMENT 'info/warning/important',
    `status`      TINYINT      DEFAULT 1 COMMENT '1=发布, 0=下架',
    `publish_by`  VARCHAR(36)  DEFAULT NULL COMMENT '发布人ID',
    `publish_at`  DATETIME     DEFAULT NULL COMMENT '发布时间',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

-- 5. comment 表：添加审核字段
ALTER TABLE `comment` ADD COLUMN `review_status` TINYINT DEFAULT 1 COMMENT '1=正常, 0=已屏蔽' AFTER `like_count`;
ALTER TABLE `comment` ADD COLUMN `review_reason` VARCHAR(200) DEFAULT NULL COMMENT '屏蔽原因' AFTER `review_status`;

-- 6. trip_publish 表：添加 content + 审核字段
ALTER TABLE `trip_publish` ADD COLUMN `content` TEXT DEFAULT NULL COMMENT '内容（与 description 同步）' AFTER `title`;
ALTER TABLE `trip_publish` ADD COLUMN `review_status` TINYINT DEFAULT 1 COMMENT '1=正常, 0=已下架, 2=待审核' AFTER `fav_count`;
ALTER TABLE `trip_publish` ADD COLUMN `review_reason` VARCHAR(200) DEFAULT NULL COMMENT '下架/审核原因' AFTER `review_status`;

-- 7. trip_days 表：添加 number 列（原表字段为 day）
ALTER TABLE `trip_days` ADD COLUMN `number` INT DEFAULT NULL COMMENT '天数序号（与 day 同步）' AFTER `trip_id`;
UPDATE `trip_days` SET `number` = `day` WHERE `number` IS NULL AND `day` IS NOT NULL;

-- 8. 将已有 description 数据同步到 content 列
UPDATE `trip_publish` SET `content` = `description` WHERE `content` IS NULL AND `description` IS NOT NULL;

-- 8. users 表：新增 status 字段（用户封禁）
ALTER TABLE `users` ADD COLUMN `status` TINYINT DEFAULT 1 COMMENT '1=正常, 0=封禁' AFTER `preferences`;

-- 9. 插入/更新默认管理员（密码: admin123）
INSERT INTO `admin_users` (`id`, `username`, `password`, `nickname`, `role`, `status`)
VALUES (REPLACE(UUID(), '-', ''), 'admin', '$2b$10$ZQ/kfW2q/M2QS55VMegCv.jj6SDxmtnFO6L4QZgDxbNSMlsyiNVtW', '超级管理员', 'super_admin', 1)
ON DUPLICATE KEY UPDATE
    `password` = '$2b$10$ZQ/kfW2q/M2QS55VMegCv.jj6SDxmtnFO6L4QZgDxbNSMlsyiNVtW',
    `nickname` = '超级管理员',
    `role`     = 'super_admin',
    `status`   = 1;

INSERT INTO `admin_users` (`id`, `username`, `password`, `nickname`, `role`, `status`)
VALUES (REPLACE(UUID(), '-', ''), 'admin2', '$2b$10$ZQ/kfW2q/M2QS55VMegCv.jj6SDxmtnFO6L4QZgDxbNSMlsyiNVtW', '管理员', 'admin', 1)
ON DUPLICATE KEY UPDATE
    `password` = '$2b$10$ZQ/kfW2q/M2QS55VMegCv.jj6SDxmtnFO6L4QZgDxbNSMlsyiNVtW',
    `nickname` = '管理员',
    `role`     = 'admin',
    `status`   = 1;
