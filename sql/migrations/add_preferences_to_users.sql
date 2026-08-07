-- 为用户添加偏好设置字段（JSON 格式）
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferences TEXT DEFAULT NULL COMMENT '用户偏好设置（JSON格式：出行方式/预算/风格等）';
