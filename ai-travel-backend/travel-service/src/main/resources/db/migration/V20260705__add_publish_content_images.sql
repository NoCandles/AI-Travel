SET @add_content_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `trip_publish` ADD COLUMN `content` TEXT DEFAULT NULL COMMENT ''发布正文'' AFTER `description`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'trip_publish'
    AND column_name = 'content'
);
PREPARE add_content_stmt FROM @add_content_sql;
EXECUTE add_content_stmt;
DEALLOCATE PREPARE add_content_stmt;

SET @add_review_status_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `trip_publish` ADD COLUMN `review_status` TINYINT DEFAULT 1 COMMENT ''1=正常, 0=已下架, 2=待审核'' AFTER `fav_count`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'trip_publish'
    AND column_name = 'review_status'
);
PREPARE add_review_status_stmt FROM @add_review_status_sql;
EXECUTE add_review_status_stmt;
DEALLOCATE PREPARE add_review_status_stmt;

SET @add_review_reason_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `trip_publish` ADD COLUMN `review_reason` VARCHAR(200) DEFAULT NULL COMMENT ''下架/审核原因'' AFTER `review_status`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'trip_publish'
    AND column_name = 'review_reason'
);
PREPARE add_review_reason_stmt FROM @add_review_reason_sql;
EXECUTE add_review_reason_stmt;
DEALLOCATE PREPARE add_review_reason_stmt;

CREATE TABLE IF NOT EXISTS `trip_publish_images` (
  `id` varchar(64) NOT NULL,
  `publish_id` varchar(64) DEFAULT NULL COMMENT '发布ID',
  `trip_plan_id` varchar(64) DEFAULT NULL COMMENT '行程ID',
  `user_id` varchar(64) DEFAULT NULL COMMENT '用户ID',
  `image_url` varchar(500) NOT NULL COMMENT '图片URL',
  `image_type` varchar(32) DEFAULT 'gallery' COMMENT 'cover/gallery',
  `source_type` varchar(32) DEFAULT 'upload' COMMENT 'trip_cover/spot_image/upload/pixabay',
  `source_id` varchar(64) DEFAULT NULL COMMENT '来源ID',
  `sort_order` int DEFAULT 0 COMMENT '排序',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_publish_id` (`publish_id`),
  KEY `idx_trip_plan_id` (`trip_plan_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布图片';
