-- 为 trip_spots 表添加 image 列（用于存储 Pixabay 抓取的景点图片 URL）
-- 此前该字段被 @TableField(exist = false) 标记，导致图片 URL 从未写入数据库
ALTER TABLE `trip_spots`
  ADD COLUMN `image` VARCHAR(500) NULL COMMENT '景点图片URL' AFTER `temperature`;
