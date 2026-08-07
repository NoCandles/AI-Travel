CREATE TABLE IF NOT EXISTS `chat_conversation` (
    `id`            VARCHAR(36)  NOT NULL COMMENT '会话ID',
    `user_id`       VARCHAR(36)  NOT NULL COMMENT '用户ID',
    `title`         VARCHAR(80)  DEFAULT NULL COMMENT '会话标题',
    `summary`       VARCHAR(255) DEFAULT NULL COMMENT '会话摘要',
    `last_message`  VARCHAR(255) DEFAULT NULL COMMENT '最后一条消息',
    `message_count` INT          DEFAULT 0 COMMENT '消息数量',
    `status`        VARCHAR(20)  DEFAULT 'active' COMMENT 'active/deleted',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_conversation_user_status` (`user_id`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天会话表';

CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id` VARCHAR(36)  NOT NULL COMMENT '会话ID',
    `user_id`         VARCHAR(36)  NOT NULL COMMENT '用户ID',
    `role`            VARCHAR(20)  DEFAULT NULL COMMENT 'system/user/assistant',
    `type`            VARCHAR(30)  DEFAULT NULL COMMENT 'text/param_card/progress/trip_card等',
    `content`         TEXT         DEFAULT NULL COMMENT '消息文本',
    `payload_json`    JSON         DEFAULT NULL COMMENT '卡片载荷',
    `client_msg_id`   VARCHAR(64)  DEFAULT NULL COMMENT '前端消息ID',
    `sort_order`      INT          DEFAULT 0 COMMENT '排序',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_message_conversation` (`conversation_id`, `sort_order`, `id`),
    KEY `idx_chat_message_user` (`user_id`, `conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息表';
