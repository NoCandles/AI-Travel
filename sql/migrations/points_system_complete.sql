-- ====================================
-- 积分系统完整建表脚本
-- 数据库: travel_app
-- ====================================

USE `travel_app`;

-- ====================================
-- 1. 用户积分账户表
-- ====================================
CREATE TABLE IF NOT EXISTS `user_points` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `user_id` VARCHAR(50) NOT NULL COMMENT '用户ID',
  `total_points` INT DEFAULT 0 COMMENT '总积分(累计获取)',
  `available_points` INT DEFAULT 0 COMMENT '可用积分',
  `used_points` INT DEFAULT 0 COMMENT '已使用积分',
  `level` INT DEFAULT 1 COMMENT '当前等级',
  `sign_in_streak` INT DEFAULT 0 COMMENT '连续签到天数',
  `last_sign_in_date` DATE COMMENT '最后签到日期',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分账户表';

-- ====================================
-- 2. 积分变动记录表
-- ====================================
CREATE TABLE IF NOT EXISTS `point_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `user_id` VARCHAR(50) NOT NULL COMMENT '用户ID',
  `change_value` INT NOT NULL COMMENT '积分变动值(正为增加,负为减少)',
  `current_balance` INT NOT NULL COMMENT '变动后余额',
  `type` VARCHAR(20) NOT NULL COMMENT '类型:earn/spend',
  `source` VARCHAR(50) NOT NULL COMMENT '来源:sign_in/publish/review/receive_like/comment/follow/ai_plan/unlock_template',
  `related_id` VARCHAR(50) COMMENT '关联ID(如旅行计划ID)',
  `description` VARCHAR(200) COMMENT '描述',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_source` (`source`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分变动记录表';

-- ====================================
-- 3. 签到记录表
-- ====================================
CREATE TABLE IF NOT EXISTS `sign_in_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `user_id` VARCHAR(50) NOT NULL COMMENT '用户ID',
  `sign_in_date` DATE NOT NULL COMMENT '签到日期',
  `points_earned` INT DEFAULT 5 COMMENT '获得积分',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_user_date` (`user_id`, `sign_in_date`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_sign_in_date` (`sign_in_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录表';

-- ====================================
-- 4. 等级配置表
-- ====================================
CREATE TABLE IF NOT EXISTS `level_config` (
  `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `level` INT NOT NULL COMMENT '等级',
  `title` VARCHAR(50) NOT NULL COMMENT '等级称号',
  `min_points` INT NOT NULL COMMENT '最低积分',
  `max_points` INT NOT NULL COMMENT '最高积分',
  `privilege` VARCHAR(200) COMMENT '等级特权描述',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级配置表';

-- ====================================
-- 5. 用户解锁模板表
-- ====================================
CREATE TABLE IF NOT EXISTS `user_unlocked_template` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `user_id` VARCHAR(50) NOT NULL COMMENT '用户ID',
  `template_id` VARCHAR(50) NOT NULL COMMENT '模板ID',
  `unlocked_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '解锁时间',
  UNIQUE KEY `uk_user_template` (`user_id`, `template_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户解锁模板表';

-- ====================================
-- 插入等级配置数据
-- ====================================
INSERT INTO `level_config` (`level`, `title`, `min_points`, `max_points`, `privilege`) VALUES
(1, '旅游新手', 0, 199, '基础功能'),
(2, '旅行爱好者', 200, 999, 'AI规划9折'),
(3, '旅行达人', 1000, 4999, '解锁高级模板免费'),
(4, '旅行大师', 5000, 19999, '专属客服标识'),
(5, '拾路传说', 20000, 999999, '全站标识')
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `min_points` = VALUES(`min_points`),
  `max_points` = VALUES(`max_points`),
  `privilege` = VALUES(`privilege`);

-- ====================================
-- 完成提示
-- ====================================
SELECT '积分系统数据库初始化完成！' AS message;
SELECT '共创建 5 张表：' AS tables;
SELECT '1. user_points - 用户积分账户表' AS table1;
SELECT '2. point_record - 积分变动记录表' AS table2;
SELECT '3. sign_in_record - 签到记录表' AS table3;
SELECT '4. level_config - 等级配置表' AS table4;
SELECT '5. user_unlocked_template - 用户解锁模板表' AS table5;
SELECT '等级配置数据已初始化！' AS level_data;
