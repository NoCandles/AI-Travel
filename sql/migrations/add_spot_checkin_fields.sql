-- 行程景点打卡字段
-- 用于行程进行中：标记景点是否已到达、到达时间

ALTER TABLE `trip_spots`
  ADD COLUMN `is_reached` TINYINT(1) DEFAULT 0 COMMENT '是否已到达（行程进行中打卡用）' AFTER `image`,
  ADD COLUMN `reached_at` DATETIME NULL DEFAULT NULL COMMENT '到达打卡时间' AFTER `is_reached`;
