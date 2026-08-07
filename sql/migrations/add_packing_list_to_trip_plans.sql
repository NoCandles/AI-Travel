-- 为 trip_plans 表添加 packing_list_json 字段，用于存储行李清单JSON数据
-- 执行日期：2026-06-27

ALTER TABLE trip_plans 
ADD COLUMN packing_list_json TEXT AFTER routes_json;

-- 添加注释
ALTER TABLE trip_plans 
MODIFY COLUMN packing_list_json TEXT COMMENT '行李清单JSON数据';
