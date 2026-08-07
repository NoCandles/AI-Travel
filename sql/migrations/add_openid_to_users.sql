-- 添加微信 OpenID 字段
ALTER TABLE `users` ADD COLUMN `open_id` VARCHAR(100) NULL COMMENT '微信OpenID' AFTER `phone`;
CREATE INDEX `idx_open_id` ON `users`(`open_id`);
