CREATE TABLE IF NOT EXISTS `im_conversation` (
    `id`                VARCHAR(36)  NOT NULL COMMENT '会话ID',
    `user_a_id`         VARCHAR(36)  NOT NULL COMMENT '用户A',
    `user_b_id`         VARCHAR(36)  NOT NULL COMMENT '用户B',
    `last_message`      VARCHAR(255) DEFAULT NULL COMMENT '最后一条消息',
    `last_message_type` VARCHAR(20)  DEFAULT NULL COMMENT 'text/route',
    `last_route_id`     VARCHAR(36)  DEFAULT NULL COMMENT '最后分享的路线ID',
    `unread_a`          INT          DEFAULT 0 COMMENT '用户A未读数',
    `unread_b`          INT          DEFAULT 0 COMMENT '用户B未读数',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_conversation_pair` (`user_a_id`, `user_b_id`),
    KEY `idx_im_conversation_a` (`user_a_id`, `updated_at`),
    KEY `idx_im_conversation_b` (`user_b_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私信会话';

CREATE TABLE IF NOT EXISTS `im_message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id` VARCHAR(36)  NOT NULL COMMENT '会话ID',
    `sender_id`       VARCHAR(36)  NOT NULL COMMENT '发送人',
    `receiver_id`     VARCHAR(36)  NOT NULL COMMENT '接收人',
    `type`            VARCHAR(20)  NOT NULL DEFAULT 'text' COMMENT 'text/route',
    `content`         TEXT         DEFAULT NULL COMMENT '文本内容',
    `route_id`        VARCHAR(36)  DEFAULT NULL COMMENT '路线ID',
    `route_title`     VARCHAR(120) DEFAULT NULL COMMENT '路线标题',
    `route_cover`     VARCHAR(500) DEFAULT NULL COMMENT '路线封面',
    `is_read`         TINYINT      DEFAULT 0 COMMENT '是否已读',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_im_message_conversation` (`conversation_id`, `created_at`, `id`),
    KEY `idx_im_message_receiver_read` (`receiver_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私信消息';
