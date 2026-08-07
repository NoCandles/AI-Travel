ALTER TABLE `trip_plans`
  ADD COLUMN `start_point` VARCHAR(100) NULL COMMENT '出发起点' AFTER `total_distance`,
  ADD COLUMN `end_point` VARCHAR(100) NULL COMMENT '返回终点' AFTER `start_point`,
  ADD COLUMN `travel_mode` VARCHAR(20) NULL COMMENT '出行方式' AFTER `end_point`;
