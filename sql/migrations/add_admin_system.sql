-- ============================================
-- 后台管理系统数据库迁移
-- 执行日期：2026-06-30
-- 说明：新增 admin_users + admin_logs 表，扩展现有表字段
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

-- 2. 初始化超级管理员（密码: admin123，BCrypt加密）
INSERT INTO `admin_users` (`id`, `username`, `password`, `nickname`, `role`)
VALUES (REPLACE(UUID(), '-', ''), 'admin',
        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh',
        '超级管理员', 'super_admin');

-- 3. 管理员操作日志表
CREATE TABLE IF NOT EXISTS `admin_logs` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `admin_id`    VARCHAR(36)  DEFAULT NULL COMMENT '操作管理员ID',
    `admin_name`  VARCHAR(50)  DEFAULT NULL COMMENT '操作管理员名',
    `module`      VARCHAR(50)  DEFAULT NULL COMMENT '操作模块: user/content/trip/points/system',
    `action`      VARCHAR(50)  DEFAULT NULL COMMENT '操作动作: create/update/delete/ban/unban/login',
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

-- 4. users表新增 status 字段（用户封禁）
ALTER TABLE `users`
    ADD COLUMN IF NOT EXISTS `status` TINYINT DEFAULT 1 COMMENT '1=正常, 0=封禁' AFTER `preferences`;

-- 5. trip_publish表新增审核字段
ALTER TABLE `trip_publish`
    ADD COLUMN IF NOT EXISTS `review_status` TINYINT DEFAULT 1 COMMENT '1=正常, 0=已下架, 2=待审核' AFTER `fav_count`,
    ADD COLUMN IF NOT EXISTS `review_reason` VARCHAR(200) DEFAULT NULL COMMENT '下架/审核原因' AFTER `review_status`;

-- 6. comment表新增审核状态字段
ALTER TABLE `comment`
    ADD COLUMN IF NOT EXISTS `review_status` TINYINT DEFAULT 1 COMMENT '1=正常, 0=已屏蔽' AFTER `like_count`,
    ADD COLUMN IF NOT EXISTS `review_reason` VARCHAR(200) DEFAULT NULL COMMENT '屏蔽原因' AFTER `review_status`;

-- 7. 敏感词库表
CREATE TABLE IF NOT EXISTS `sensitive_words` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `word`       VARCHAR(100) NOT NULL COMMENT '敏感词',
    `level`      TINYINT      DEFAULT 1 COMMENT '1=普通, 2=严重',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库';

-- 8. 系统公告表
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
