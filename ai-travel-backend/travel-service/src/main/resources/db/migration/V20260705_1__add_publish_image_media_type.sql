SET @add_publish_image_media_type_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `trip_publish_images` ADD COLUMN `media_type` VARCHAR(32) DEFAULT ''image'' COMMENT ''image/video/live'' AFTER `image_type`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'trip_publish_images'
    AND column_name = 'media_type'
);
PREPARE add_publish_image_media_type_stmt FROM @add_publish_image_media_type_sql;
EXECUTE add_publish_image_media_type_stmt;
DEALLOCATE PREPARE add_publish_image_media_type_stmt;
