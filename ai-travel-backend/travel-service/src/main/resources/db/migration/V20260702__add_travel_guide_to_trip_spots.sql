-- 为 trip_spots 表添加 travel_guide 字段
-- 用于存储景点的游玩推荐/攻略信息

ALTER TABLE trip_spots 
ADD COLUMN travel_guide TEXT COMMENT '游玩推荐/攻略' AFTER tips;
