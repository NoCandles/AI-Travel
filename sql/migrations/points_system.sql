-- =============================================
-- 积分系统数据库表
-- =============================================

-- 1. 用户积分账户表
CREATE TABLE IF NOT EXISTS `user_points` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户ID',
    `total_points` INT DEFAULT 0 COMMENT '总积分(累计获取)',
    `available_points` INT DEFAULT 0 COMMENT '可用积分',
    `used_points` INT DEFAULT 0 COMMENT '已使用积分',
    `level` INT DEFAULT 1 COMMENT '当前等级',
    `sign_in_streak` INT DEFAULT 0 COMMENT '连续签到天数',
    `last_sign_in_date` DATE COMMENT '最后签到日期',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分账户表';

-- 2. 积分变动记录表
CREATE TABLE IF NOT EXISTS `point_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(50) NOT NULL COMMENT '用户ID',
    `change_value` INT NOT NULL COMMENT '积分变动值(正为增加,负为减少)',
    `current_balance` INT NOT NULL COMMENT '变动后余额',
    `type` VARCHAR(20) NOT NULL COMMENT '类型:earn/spend',
    `source` VARCHAR(50) NOT NULL COMMENT '来源:sign_in/publish/review/receive_like/comment/follow/complete_profile/ai_plan/unlock_template',
    `related_id` VARCHAR(50) COMMENT '关联ID(如旅行计划ID)',
    `description` VARCHAR(200) COMMENT '描述',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_source` (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分变动记录表';

-- 3. 签到记录表
CREATE TABLE IF NOT EXISTS `sign_in_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(50) NOT NULL COMMENT '用户ID',
    `sign_in_date` DATE NOT NULL COMMENT '签到日期',
    `points_earned` INT DEFAULT 5 COMMENT '获得积分',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_date` (`user_id`, `sign_in_date`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录表';

-- 4. 等级配置表
CREATE TABLE IF NOT EXISTS `level_config` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `level` INT NOT NULL UNIQUE COMMENT '等级',
    `min_points` INT NOT NULL COMMENT '所需最小积分',
    `max_points` INT NOT NULL COMMENT '所需最大积分',
    `title` VARCHAR(50) NOT NULL COMMENT '称号',
    `privilege` VARCHAR(200) COMMENT '特权描述',
    `icon_url` VARCHAR(200) COMMENT '等级图标',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级配置表';

-- 插入等级配置数据
INSERT INTO `level_config` (`level`, `min_points`, `max_points`, `title`, `privilege`) VALUES
(1, 0, 199, '旅游新手', '基础功能'),
(2, 200, 999, '旅行爱好者', 'AI规划9折(消耗9积分)'),
(3, 1000, 4999, '旅行达人', '解锁高级模板免费'),
(4, 5000, 19999, '旅行大师', '专属客服标识'),
(5, 20000, 999999, '拾路传说', '全站标识');

-- 5. 用户解锁模板记录表
CREATE TABLE IF NOT EXISTS `user_unlocked_template` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(50) NOT NULL COMMENT '用户ID',
    `template_id` VARCHAR(50) NOT NULL COMMENT '模板ID',
    `unlocked_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_template` (`user_id`, `template_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户解锁模板记录表';
