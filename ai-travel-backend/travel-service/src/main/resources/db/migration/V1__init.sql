-- ============================================================
-- 🏕️ 拾路派 (PathFinder) — 完整数据库建库脚本
-- 数据库：travel_app
-- 编码：utf8mb4 / InnoDB
-- 说明：直接执行即可完成全部建表 + 种子数据初始化
--        所有 CREATE TABLE 使用 IF NOT EXISTS，可重复执行
--        所有 INSERT 使用 IGNORE / ON DUPLICATE KEY UPDATE
--        实体类与表结构一一对应，字段顺序与类型完全匹配
-- 日期：2026-07-03
-- ============================================================

CREATE DATABASE IF NOT EXISTS `travel_app`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `travel_app`;

-- ============================================================
-- 1️⃣ 用户表 users
--    User.java → @TableName("users")
--    实体字段：id, nickname, avatar, gender, city, signature,
--            country, province, openId, phone, password,
--            preferences, status, createdAt, updatedAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `users` (
    `id`          VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `nickname`    VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `gender`      TINYINT      DEFAULT NULL COMMENT '性别 0未知/1男/2女',
    `city`        VARCHAR(50)  DEFAULT NULL COMMENT '城市',
    `signature`   VARCHAR(200) DEFAULT NULL COMMENT '个性签名',
    `country`     VARCHAR(50)  DEFAULT NULL COMMENT '国家',
    `province`    VARCHAR(50)  DEFAULT NULL COMMENT '省份',
    `open_id`     VARCHAR(100) DEFAULT NULL COMMENT '微信OpenID',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `password`    VARCHAR(200) DEFAULT NULL COMMENT '密码(BCrypt)',
    `preferences` TEXT         DEFAULT NULL COMMENT '偏好设置(JSON)',
    `status`      TINYINT      DEFAULT 1 COMMENT '1=正常, 0=封禁',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_open_id` (`open_id`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2️⃣ 行程计划表 trip_plans
--    TripPlan.java → @TableName("trip_plans")
--    实体字段：id, name, destination, startDate, endDate,
--            description, status, userId, preferences,
--            mustVisitPlaces, budget, totalDistance,
--            startPoint, endPoint, travelMode,
--            routesJson, versionsJson, packingListJson,
--            createdAt, updatedAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `trip_plans` (
    `id`                VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `name`              VARCHAR(200) DEFAULT NULL COMMENT '行程名称',
    `destination`       VARCHAR(200) DEFAULT NULL COMMENT '目的地',
    `start_date`        VARCHAR(20)  DEFAULT NULL COMMENT '开始日期 yyyy-MM-dd',
    `end_date`          VARCHAR(20)  DEFAULT NULL COMMENT '结束日期 yyyy-MM-dd',
    `description`       TEXT         DEFAULT NULL COMMENT '行程描述',
    `status`            VARCHAR(20)  DEFAULT 'draft' COMMENT '状态: draft/planning/generating/completed/cancelled',
    `user_id`           VARCHAR(36)  DEFAULT NULL COMMENT '用户ID',
    `preferences`       VARCHAR(200) DEFAULT NULL COMMENT '出行偏好',
    `must_visit_places` TEXT         DEFAULT NULL COMMENT '必去景点(JSON数组)',
    `budget`            VARCHAR(20)  DEFAULT NULL COMMENT '预算: 经济实惠/中等预算/高端享受',
    `total_distance`    INT          DEFAULT NULL COMMENT '总行程距离(米)',
    `start_point`       VARCHAR(100) DEFAULT NULL COMMENT '出发起点',
    `end_point`         VARCHAR(100) DEFAULT NULL COMMENT '返回终点',
    `travel_mode`       VARCHAR(20)  DEFAULT NULL COMMENT '出行方式: drive/transit/hiking',
    `routes_json`       MEDIUMTEXT   DEFAULT NULL COMMENT '路线JSON数据',
    `versions_json`     MEDIUMTEXT   DEFAULT NULL COMMENT '版本快照JSON',
    `packing_list_json` TEXT         DEFAULT NULL COMMENT '行李清单JSON',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_destination` (`destination`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程计划表';

-- ============================================================
-- 3️⃣ 行程天表 trip_days
--    TripDay.java → @TableName("trip_days")
--    实体字段：id, tripId, day, date, weather, temperature,
--            notes, hotelName, hotelDetail, hotelPrice,
--            createdAt, updatedAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `trip_days` (
    `id`           VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `trip_id`      VARCHAR(36)  NOT NULL COMMENT '关联行程ID',
    `day`          INT          DEFAULT NULL COMMENT '天数序号(从1开始)',
    `date`         VARCHAR(20)  DEFAULT NULL COMMENT '具体日期',
    `weather`      VARCHAR(50)  DEFAULT NULL COMMENT '天气',
    `temperature`  VARCHAR(50)  DEFAULT NULL COMMENT '温度',
    `notes`        TEXT         DEFAULT NULL COMMENT '当日备注',
    `hotel_name`   VARCHAR(200) DEFAULT NULL COMMENT '推荐酒店名',
    `hotel_detail` TEXT         DEFAULT NULL COMMENT '酒店详情JSON',
    `hotel_price`  INT          DEFAULT NULL COMMENT '酒店价格',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_trip_id` (`trip_id`),
    KEY `idx_day` (`day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程天表';

-- ============================================================
-- 4️⃣ 行程景点表 trip_spots
--    TripSpot.java → @TableName("trip_spots")
--    实体字段：id, tripDayId, name, category, address,
--            latitude, longitude, orderNum, arrivalTime,
--            departureTime, duration, cost, tips, travelGuide,
--            temperature, image, isReached, reachedAt,
--            createdAt, updatedAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `trip_spots` (
    `id`             VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `trip_day_id`    VARCHAR(36)  DEFAULT NULL COMMENT '关联天ID',
    `name`           VARCHAR(200) DEFAULT NULL COMMENT '景点名称',
    `category`       VARCHAR(50)  DEFAULT NULL COMMENT '景点分类',
    `address`        VARCHAR(500) DEFAULT NULL COMMENT '地址',
    `latitude`       DOUBLE       DEFAULT NULL COMMENT '纬度',
    `longitude`      DOUBLE       DEFAULT NULL COMMENT '经度',
    `order_num`      INT          DEFAULT NULL COMMENT '排序序号',
    `arrival_time`   VARCHAR(20)  DEFAULT NULL COMMENT '到达时间',
    `departure_time` VARCHAR(20)  DEFAULT NULL COMMENT '离开时间',
    `duration`       VARCHAR(50)  DEFAULT NULL COMMENT '游览时长',
    `cost`           VARCHAR(50)  DEFAULT NULL COMMENT '费用',
    `tips`           TEXT         DEFAULT NULL COMMENT '小贴士',
    `travel_guide`   TEXT         DEFAULT NULL COMMENT '游玩推荐/攻略',
    `temperature`    VARCHAR(50)  DEFAULT NULL COMMENT '景点气温',
    `image`          VARCHAR(500) DEFAULT NULL COMMENT '景点图片URL',
    `is_reached`     TINYINT(1)   DEFAULT 0 COMMENT '是否已到达(打卡用)',
    `reached_at`     DATETIME     DEFAULT NULL COMMENT '到达打卡时间',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_trip_day_id` (`trip_day_id`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程景点表';

-- ============================================================
-- 5️⃣ 行程发布表（广场文章） trip_publish
--    TripPublish.java → @TableName("trip_publish")
--    实体字段：id, userId, tripPlanId, title, description,
--            coverImage, location, days, nights, tags, status,
--            viewCount, likeCount, commentCount, favCount,
--            reviewStatus, reviewReason, content,
--            deleted, createdAt, updatedAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `trip_publish` (
    `id`            VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `user_id`       VARCHAR(36)  DEFAULT NULL COMMENT '发布者用户ID',
    `trip_plan_id`  VARCHAR(36)  DEFAULT NULL COMMENT '关联行程计划ID',
    `title`         VARCHAR(200) DEFAULT NULL COMMENT '标题',
    `content`       TEXT         DEFAULT NULL COMMENT '正文内容',
    `description`   TEXT         DEFAULT NULL COMMENT '描述摘要',
    `cover_image`   VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `location`      VARCHAR(200) DEFAULT NULL COMMENT '目的地/地点',
    `days`          INT          DEFAULT NULL COMMENT '天数',
    `nights`        INT          DEFAULT NULL COMMENT '晚数',
    `tags`          VARCHAR(500) DEFAULT NULL COMMENT '标签(JSON数组)',
    `status`        TINYINT      DEFAULT 1 COMMENT '状态: 0=草稿 1=已发布',
    `view_count`    INT          DEFAULT 0 COMMENT '浏览数',
    `like_count`    INT          DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT          DEFAULT 0 COMMENT '评论数',
    `fav_count`     INT          DEFAULT 0 COMMENT '收藏数',
    `review_status` TINYINT      DEFAULT 1 COMMENT '1=正常 0=已下架 2=待审核',
    `review_reason` VARCHAR(200) DEFAULT NULL COMMENT '下架/审核原因',
    `deleted`       TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=正常 1=已删除',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_location` (`location`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程发布/广场文章表';

-- ============================================================
-- 6️⃣ 评论表 comment
--    Comment.java → @TableName("comment")
--    实体字段：id, publishId, userId, parentId, content,
--            likeCount, reviewStatus, reviewReason,
--            deleted, createdAt, updatedAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `comment` (
    `id`            VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `publish_id`    VARCHAR(36)  DEFAULT NULL COMMENT '关联发布ID',
    `user_id`       VARCHAR(36)  DEFAULT NULL COMMENT '评论者用户ID',
    `parent_id`     VARCHAR(36)  DEFAULT NULL COMMENT '父评论ID(回复用)',
    `content`       TEXT         DEFAULT NULL COMMENT '评论内容',
    `like_count`    INT          DEFAULT 0 COMMENT '点赞数',
    `review_status` TINYINT      DEFAULT 1 COMMENT '1=正常 0=已屏蔽',
    `review_reason` VARCHAR(200) DEFAULT NULL COMMENT '屏蔽原因',
    `deleted`       TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=正常 1=已删除',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_publish_id` (`publish_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ============================================================
-- 7️⃣ 点赞/收藏记录表 like_record
--    LikeRecord.java → @TableName("like_record")
--    实体字段：id, userId, targetId, targetType, actionType, createdAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `like_record` (
    `id`          VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `user_id`     VARCHAR(36)  NOT NULL COMMENT '操作用户ID',
    `target_id`   VARCHAR(36)  NOT NULL COMMENT '目标ID(发布ID或评论ID)',
    `target_type` VARCHAR(20)  NOT NULL COMMENT '目标类型: publish/comment',
    `action_type` VARCHAR(20)  NOT NULL COMMENT '操作类型: like/fav',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target_action` (`user_id`, `target_id`, `action_type`),
    KEY `idx_target` (`target_id`, `target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞/收藏记录表';

-- ============================================================
-- 8️⃣ 关注关系表 follow_relation
--    FollowRelation.java → @TableName("follow_relation")
--    实体字段：id, followerId, followingId, createdAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `follow_relation` (
    `id`           VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `follower_id`  VARCHAR(36)  NOT NULL COMMENT '关注者用户ID',
    `following_id` VARCHAR(36)  NOT NULL COMMENT '被关注用户ID',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follow` (`follower_id`, `following_id`),
    KEY `idx_follower` (`follower_id`),
    KEY `idx_following` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';

-- ============================================================
-- 9️⃣ 消息通知表 message
--    Message.java → @TableName("message")
--    实体字段：id, userId, senderId, type, content,
--            targetId, targetType, isRead, createdAt, updatedAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `message` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`     VARCHAR(36)  DEFAULT NULL COMMENT '接收者用户ID',
    `sender_id`   VARCHAR(36)  DEFAULT NULL COMMENT '发送者用户ID',
    `type`        VARCHAR(20)  DEFAULT NULL COMMENT '通知类型: like/comment/follow/system',
    `content`     TEXT         DEFAULT NULL COMMENT '消息内容',
    `target_id`   VARCHAR(100) DEFAULT NULL COMMENT '关联目标ID',
    `target_type` VARCHAR(20)  DEFAULT NULL COMMENT '关联目标类型',
    `is_read`     INT          DEFAULT 0 COMMENT '是否已读: 0=未读 1=已读',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_is_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- ============================================================
-- 🔟 景点缓存表 attractions
--    Attraction.java → @TableName("attractions")
--    用途：缓存腾讯地图POI搜索结果 → ChatService 加载景点→城市映射
--    ChatServiceImpl 查询此表做景点名→目的地城市匹配
-- ============================================================
CREATE TABLE IF NOT EXISTS `attractions` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `attraction_id` VARCHAR(64)  DEFAULT NULL COMMENT '腾讯地图POI ID',
    `name`          VARCHAR(200) DEFAULT NULL COMMENT '景点名称',
    `address`       VARCHAR(500) DEFAULT NULL COMMENT '地址',
    `tel`           VARCHAR(50)  DEFAULT NULL COMMENT '电话',
    `latitude`      DOUBLE       DEFAULT NULL COMMENT '纬度',
    `longitude`     DOUBLE       DEFAULT NULL COMMENT '经度',
    `distance`      INT          DEFAULT NULL COMMENT '距离(米)',
    `city`          VARCHAR(50)  DEFAULT NULL COMMENT '所属城市',
    `category`      VARCHAR(200) DEFAULT NULL COMMENT 'POI分类',
    `tags`          VARCHAR(500) DEFAULT NULL COMMENT '标签',
    `source`        VARCHAR(50)  DEFAULT NULL COMMENT '数据来源: tencent_map/seed',
    `search_lat`    DOUBLE       DEFAULT NULL COMMENT '搜索时中心纬度',
    `search_lng`    DOUBLE       DEFAULT NULL COMMENT '搜索时中心经度',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_attraction_id` (`attraction_id`),
    KEY `idx_city` (`city`),
    KEY `idx_search_lat_lng` (`search_lat`, `search_lng`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景点缓存表';

-- ============================================================
-- 1️⃣1️⃣ 酒店缓存表 hotels
--    Hotel.java → @TableName("hotels")
-- ============================================================
CREATE TABLE IF NOT EXISTS `hotels` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `hotel_id`   VARCHAR(64)  DEFAULT NULL COMMENT '酒店POI ID',
    `name`       VARCHAR(200) DEFAULT NULL COMMENT '酒店名称',
    `address`    VARCHAR(500) DEFAULT NULL COMMENT '地址',
    `tel`        VARCHAR(50)  DEFAULT NULL COMMENT '电话',
    `latitude`   DOUBLE       DEFAULT NULL COMMENT '纬度',
    `longitude`  DOUBLE       DEFAULT NULL COMMENT '经度',
    `distance`   INT          DEFAULT NULL COMMENT '距离(米)',
    `city`       VARCHAR(50)  DEFAULT NULL COMMENT '所属城市',
    `category`   VARCHAR(200) DEFAULT NULL COMMENT '酒店分类/星级',
    `tags`       VARCHAR(500) DEFAULT NULL COMMENT '标签',
    `source`     VARCHAR(50)  DEFAULT NULL COMMENT '数据来源',
    `search_lat` DOUBLE       DEFAULT NULL COMMENT '搜索时中心纬度',
    `search_lng` DOUBLE       DEFAULT NULL COMMENT '搜索时中心经度',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hotel_id` (`hotel_id`),
    KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='酒店缓存表';

-- ============================================================
-- 1️⃣2️⃣ 城市表 city
--    City.java → @TableName("city")
--    实体字段：id, name, pinyin, pinyinShort, hot, lng, lat, createdAt, updatedAt
-- ============================================================
CREATE TABLE IF NOT EXISTS `city` (
    `id`           INT          NOT NULL COMMENT '行政区划代码(主键)',
    `name`         VARCHAR(50)  NOT NULL COMMENT '城市名称',
    `pinyin`       VARCHAR(200) DEFAULT NULL COMMENT '全拼',
    `pinyin_short` VARCHAR(10)  DEFAULT NULL COMMENT '拼音首字母 A-Z',
    `hot`          INT          DEFAULT 0 COMMENT '热度排序 0-99',
    `lng`          DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `lat`          DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_hot` (`hot`),
    KEY `idx_pinyin_short` (`pinyin_short`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='城市表';

-- ============================================================
-- 1️⃣3️⃣ 徒步路线表 hiking_route
--    HikingRoute.java → @TableName("hiking_route")
-- ============================================================
CREATE TABLE IF NOT EXISTS `hiking_route` (
    `id`                VARCHAR(36)   NOT NULL COMMENT 'UUID主键',
    `trip_plan_id`      VARCHAR(36)   DEFAULT NULL COMMENT '关联行程ID',
    `route_name`        VARCHAR(200)  DEFAULT NULL COMMENT '路线名称',
    `total_distance`    DECIMAL(10,2) DEFAULT NULL COMMENT '总里程(km)',
    `total_ascent`      INT           DEFAULT NULL COMMENT '累计爬升(m)',
    `total_descent`     INT           DEFAULT NULL COMMENT '累计下降(m)',
    `walk_time`         VARCHAR(50)   DEFAULT NULL COMMENT '纯步行时长',
    `total_time`        VARCHAR(50)   DEFAULT NULL COMMENT '含休息总时长',
    `difficulty`        INT           DEFAULT NULL COMMENT '难度 1-5',
    `route_type`        VARCHAR(20)   DEFAULT NULL COMMENT '路线类型: 环线/单程/往返/登山穿越/溯溪/古道',
    `road_ratio`        VARCHAR(500)  DEFAULT NULL COMMENT '路况占比(JSON)',
    `has_campsite`      INT           DEFAULT 0 COMMENT '是否有露营地: 0=否 1=是',
    `is_family_friendly` INT          DEFAULT 0 COMMENT '是否亲子友好: 0=否 1=是',
    `cover_image`       VARCHAR(500)  DEFAULT NULL COMMENT '封面图URL',
    `safety_json`       TEXT          DEFAULT NULL COMMENT '安全指南JSON',
    `gear_json`         TEXT          DEFAULT NULL COMMENT '装备建议JSON',
    `supply_json`       TEXT          DEFAULT NULL COMMENT '补给信息JSON',
    `is_public`         INT           DEFAULT 1 COMMENT '是否公开到广场: 0=否 1=是',
    `view_count`        INT           DEFAULT 0 COMMENT '浏览次数',
    `like_count`        INT           DEFAULT 0 COMMENT '点赞数',
    `creator_id`        VARCHAR(36)   DEFAULT NULL COMMENT '创建者用户ID',
    `source`            VARCHAR(20)   DEFAULT 'ai' COMMENT '来源: ai/manual',
    `created_at`        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_trip_plan_id` (`trip_plan_id`),
    KEY `idx_creator_id` (`creator_id`),
    KEY `idx_is_public` (`is_public`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='徒步路线表';

-- ============================================================
-- 1️⃣4️⃣ 徒步路段表 hiking_segment
--    HikingSegment.java → @TableName("hiking_segment")
-- ============================================================
CREATE TABLE IF NOT EXISTS `hiking_segment` (
    `id`               VARCHAR(36)   NOT NULL COMMENT 'UUID主键',
    `hiking_route_id`  VARCHAR(36)   NOT NULL COMMENT '关联徒步路线ID',
    `day_num`          INT           DEFAULT NULL COMMENT '第几天',
    `order_num`        INT           DEFAULT NULL COMMENT '段内排序',
    `name`             VARCHAR(200)  DEFAULT NULL COMMENT '路段名称',
    `distance`         DECIMAL(10,2) DEFAULT NULL COMMENT '距离(km)',
    `ascent`           INT           DEFAULT NULL COMMENT '爬升(m)',
    `descent`          INT           DEFAULT NULL COMMENT '下降(m)',
    `road_type`        VARCHAR(20)   DEFAULT NULL COMMENT '路况: 土路/台阶/栈道/野路',
    `slope`            VARCHAR(20)   DEFAULT NULL COMMENT '坡度: 缓坡/中坡/陡坡',
    `highlights`       TEXT          DEFAULT NULL COMMENT '亮点景观',
    `rest_point`       VARCHAR(200)  DEFAULT NULL COMMENT '休息点位置',
    `risk_tip`         TEXT          DEFAULT NULL COMMENT '风险提示',
    `signal_strength`  VARCHAR(20)   DEFAULT NULL COMMENT '信号: 良好/弱/无信号',
    `start_point_name` VARCHAR(200)  DEFAULT NULL COMMENT '起点名称',
    `end_point_name`   VARCHAR(200)  DEFAULT NULL COMMENT '终点名称',
    `start_lat`        DECIMAL(10,6) DEFAULT NULL COMMENT '起点纬度',
    `start_lng`        DECIMAL(10,6) DEFAULT NULL COMMENT '起点经度',
    `end_lat`          DECIMAL(10,6) DEFAULT NULL COMMENT '终点纬度',
    `end_lng`          DECIMAL(10,6) DEFAULT NULL COMMENT '终点经度',
    `created_at`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_hiking_route_id` (`hiking_route_id`),
    KEY `idx_day_num` (`day_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='徒步路段表';

-- ============================================================
-- 1️⃣5️⃣ 用户积分账户表 user_points
--    UserPoints.java → @TableName("user_points")
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_points` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`          VARCHAR(50) NOT NULL COMMENT '用户ID',
    `total_points`     INT         DEFAULT 0 COMMENT '总积分(累计获取)',
    `available_points` INT         DEFAULT 0 COMMENT '可用积分',
    `used_points`      INT         DEFAULT 0 COMMENT '已使用积分',
    `level`            INT         DEFAULT 1 COMMENT '当前等级',
    `sign_in_streak`   INT         DEFAULT 0 COMMENT '连续签到天数',
    `last_sign_in_date` DATE       DEFAULT NULL COMMENT '最后签到日期',
    `created_at`       DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分账户表';

-- ============================================================
-- 1️⃣6️⃣ 积分变动记录表 point_record
--    PointRecord.java → @TableName("point_record")
-- ============================================================
CREATE TABLE IF NOT EXISTS `point_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`         VARCHAR(50)  NOT NULL COMMENT '用户ID',
    `change_value`    INT          NOT NULL COMMENT '积分变动值(正=增加,负=减少)',
    `current_balance` INT          NOT NULL COMMENT '变动后余额',
    `type`            VARCHAR(20)  NOT NULL COMMENT '类型: earn/spend',
    `source`          VARCHAR(50)  NOT NULL COMMENT '来源: sign_in/publish/review/receive_like/comment/follow/ai_plan/unlock_template',
    `related_id`      VARCHAR(50)  DEFAULT NULL COMMENT '关联ID',
    `description`     VARCHAR(200) DEFAULT NULL COMMENT '描述',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_source` (`source`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分变动记录表';

-- ============================================================
-- 1️⃣7️⃣ 签到记录表 sign_in_record
--    SignInRecord.java → @TableName("sign_in_record")
-- ============================================================
CREATE TABLE IF NOT EXISTS `sign_in_record` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`       VARCHAR(50) NOT NULL COMMENT '用户ID',
    `sign_in_date`  DATE        NOT NULL COMMENT '签到日期',
    `points_earned` INT         DEFAULT 5 COMMENT '获得积分',
    `created_at`    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `sign_in_date`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_sign_in_date` (`sign_in_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录表';

-- ============================================================
-- 1️⃣8️⃣ 等级配置表 level_config
--    LevelConfig.java → @TableName("level_config")
-- ============================================================
CREATE TABLE IF NOT EXISTS `level_config` (
    `id`         INT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `level`      INT          NOT NULL COMMENT '等级 1-5',
    `min_points` INT          NOT NULL COMMENT '最低积分',
    `max_points` INT          NOT NULL COMMENT '最高积分',
    `title`      VARCHAR(50)  NOT NULL COMMENT '等级称号',
    `privilege`  VARCHAR(200) DEFAULT NULL COMMENT '等级特权描述',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级配置表';

-- ============================================================
-- 1️⃣9️⃣ 用户解锁模板表 user_unlocked_template
--    UserUnlockedTemplate.java → @TableName("user_unlocked_template")
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_unlocked_template` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`     VARCHAR(50) NOT NULL COMMENT '用户ID',
    `template_id` VARCHAR(50) NOT NULL COMMENT '模板ID',
    `unlocked_at` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '解锁时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_template` (`user_id`, `template_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户解锁模板表';

-- ============================================================
-- 2️⃣0️⃣ 管理员账号表 admin_users
--    AdminUser.java → @TableName("admin_users")
-- ============================================================
CREATE TABLE IF NOT EXISTS `admin_users` (
    `id`         VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `username`   VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`   VARCHAR(200) NOT NULL COMMENT 'BCrypt加密密码',
    `nickname`   VARCHAR(50)  DEFAULT NULL COMMENT '显示名称',
    `role`       VARCHAR(20)  DEFAULT 'admin' COMMENT 'admin=普通管理员 super_admin=超级管理员',
    `status`     TINYINT      DEFAULT 1 COMMENT '1=正常 0=禁用',
    `last_login` DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号表';

-- ============================================================
-- 2️⃣1️⃣ 管理员操作日志表 admin_logs
--    AdminLog.java → @TableName("admin_logs")
-- ============================================================
CREATE TABLE IF NOT EXISTS `admin_logs` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `admin_id`    VARCHAR(36)  DEFAULT NULL COMMENT '操作管理员ID',
    `admin_name`  VARCHAR(50)  DEFAULT NULL COMMENT '操作管理员名',
    `module`      VARCHAR(50)  DEFAULT NULL COMMENT '操作模块: user/content/trip/points/system',
    `action`      VARCHAR(50)  DEFAULT NULL COMMENT '操作动作: create/update/delete/ban/unban/login',
    `target_type` VARCHAR(50)  DEFAULT NULL COMMENT '目标类型',
    `target_id`   VARCHAR(100) DEFAULT NULL COMMENT '目标ID',
    `detail`      TEXT         DEFAULT NULL COMMENT 'JSON格式操作详情',
    `ip`          VARCHAR(45)  DEFAULT NULL COMMENT '操作IP',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_module` (`module`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志表';

-- ============================================================
-- 2️⃣2️⃣ 敏感词库表 sensitive_words
--    SensitiveWord.java → @TableName("sensitive_words")
-- ============================================================
CREATE TABLE IF NOT EXISTS `sensitive_words` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `word`       VARCHAR(100) NOT NULL COMMENT '敏感词',
    `level`      TINYINT      DEFAULT 1 COMMENT '1=普通 2=严重',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库';

-- ============================================================
-- 2️⃣3️⃣ 系统公告表 system_announcements
--    (无独立实体类，由管理后台直接操作用)
-- ============================================================
CREATE TABLE IF NOT EXISTS `system_announcements` (
    `id`          VARCHAR(36)  NOT NULL COMMENT 'UUID主键',
    `title`       VARCHAR(200) NOT NULL COMMENT '公告标题',
    `content`     TEXT         NOT NULL COMMENT '公告内容',
    `level`       VARCHAR(20)  DEFAULT 'info' COMMENT 'info/warning/important',
    `status`      TINYINT      DEFAULT 1 COMMENT '1=发布 0=下架',
    `publish_by`  VARCHAR(36)  DEFAULT NULL COMMENT '发布人ID',
    `publish_at`  DATETIME     DEFAULT NULL COMMENT '发布时间',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';


-- ============================================================
-- 📦 种子数据
-- ============================================================

-- ══════ 等级配置 ══════
INSERT INTO `level_config` (`level`, `min_points`, `max_points`, `title`, `privilege`) VALUES
(1, 0,    199,    '旅游新手', '基础功能'),
(2, 200,  999,    '旅行爱好者', 'AI规划9折'),
(3, 1000, 4999,   '旅行达人', '解锁高级模板免费'),
(4, 5000, 19999,  '旅行大师', '专属客服标识'),
(5, 20000, 999999, '拾路传说', '全站标识')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `privilege` = VALUES(`privilege`);

-- ══════ 管理员账号（密码: admin123） ══════
INSERT IGNORE INTO `admin_users` (`id`, `username`, `password`, `nickname`, `role`, `status`)
VALUES ('admin-001', 'admin',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '超级管理员', 'super_admin', 1);

-- ══════ 热门城市数据 ══════
INSERT IGNORE INTO `city` (`id`, `name`, `pinyin`, `pinyin_short`, `hot`, `lng`, `lat`) VALUES
(110100, '北京',   'beijing',   'B', 99, 116.4074, 39.9042),
(310100, '上海',   'shanghai',  'S', 98, 121.4737, 31.2304),
(440100, '广州',   'guangzhou', 'G', 95, 113.2644, 23.1291),
(440300, '深圳',   'shenzhen',  'S', 94, 114.0579, 22.5431),
(330100, '杭州',   'hangzhou',  'H', 93, 120.1551, 30.2741),
(510100, '成都',   'chengdu',   'C', 92, 104.0665, 30.5728),
(320100, '南京',   'nanjing',   'N', 90, 118.7969, 32.0603),
(420100, '武汉',   'wuhan',     'W', 89, 114.3054, 30.5931),
(500000, '重庆',   'chongqing', 'C', 88, 106.5516, 29.5630),
(610100, '西安',   'xian',      'X', 87, 108.9402, 34.3416),
(350200, '厦门',   'xiamen',    'X', 86, 118.0894, 24.4798),
(370200, '青岛',   'qingdao',   'Q', 85, 120.3826, 36.0671),
(460100, '三亚',   'sanya',     'S', 84, 109.5119, 18.2528),
(530100, '昆明',   'kunming',   'K', 83, 102.8329, 24.8801),
(532900, '大理',   'dali',      'D', 82, 100.2676, 25.6065),
(530700, '丽江',   'lijiang',   'L', 81, 100.2271, 26.8567),
(320500, '苏州',   'suzhou',    'S', 80, 120.5853, 31.2989),
(450300, '桂林',   'guilin',    'G', 79, 110.2900, 25.2736),
(540100, '拉萨',   'lasa',      'L', 78, 91.1719, 29.6500),
(410100, '郑州',   'zhengzhou', 'Z', 75, 113.6254, 34.7466),
(430100, '长沙',   'changsha',  'C', 74, 112.9388, 28.2278),
(210200, '大连',   'dalian',    'D', 72, 121.6147, 38.9140),
(110000, '天津',   'tianjin',   'T', 70, 117.1902, 39.1252),
(330200, '宁波',   'ningbo',    'N', 68, 121.5430, 29.8683),
(513200, '阿坝',   'aba',       'A', 65, 102.2286, 31.8997),
(620100, '兰州',   'lanzhou',   'L', 63, 103.8343, 36.0611),
(630100, '西宁',   'xining',    'X', 62, 101.7782, 36.6171),
(360100, '南昌',   'nanchang',  'N', 60, 115.8582, 28.6820),
(350100, '福州',   'fuzhou',    'F', 58, 119.2965, 26.0745),
(410300, '洛阳',   'luoyang',   'L', 55, 112.4536, 34.6181),
(130100, '石家庄', 'shijiazhuang','S', 50, 114.5149, 38.0428),
(230100, '哈尔滨', 'haerbin',   'H', 48, 126.5342, 45.8038),
(520100, '贵阳',   'guiyang',   'G', 45, 106.6303, 26.6465),
(440400, '珠海',   'zhuhai',    'Z', 42, 113.5767, 22.2707),
(450500, '北海',   'beihai',    'B', 40, 109.1200, 21.4811),
(640100, '银川',   'yinchuan',  'Y', 38, 106.2309, 38.4872),
(340100, '合肥',   'hefei',     'H', 35, 117.2273, 31.8206);

-- ══════ 景点种子数据（ChatService 景点→城市映射） ══════
INSERT IGNORE INTO `attractions` (`attraction_id`, `name`, `city`, `category`, `source`) VALUES
('seed_001', '鼓浪屿',     '厦门', '景点', 'seed'),
('seed_002', '曾厝垵',     '厦门', '景点', 'seed'),
('seed_003', '南普陀寺',   '厦门', '景点', 'seed'),
('seed_004', '厦门大学',   '厦门', '景点', 'seed'),
('seed_005', '环岛路',     '厦门', '景点', 'seed'),
('seed_006', '宽窄巷子',   '成都', '景点', 'seed'),
('seed_007', '锦里',       '成都', '景点', 'seed'),
('seed_008', '武侯祠',     '成都', '景点', 'seed'),
('seed_009', '大熊猫繁育研究基地', '成都', '景点', 'seed'),
('seed_010', '春熙路',     '成都', '景点', 'seed'),
('seed_011', '故宫',       '北京', '景点', 'seed'),
('seed_012', '天安门广场', '北京', '景点', 'seed'),
('seed_013', '长城',       '北京', '景点', 'seed'),
('seed_014', '颐和园',     '北京', '景点', 'seed'),
('seed_015', '天坛',       '北京', '景点', 'seed'),
('seed_016', '西湖',       '杭州', '景点', 'seed'),
('seed_017', '灵隐寺',     '杭州', '景点', 'seed'),
('seed_018', '千岛湖',     '杭州', '景点', 'seed'),
('seed_019', '洪崖洞',     '重庆', '景点', 'seed'),
('seed_020', '解放碑',     '重庆', '景点', 'seed'),
('seed_021', '磁器口',     '重庆', '景点', 'seed'),
('seed_022', '大雁塔',     '西安', '景点', 'seed'),
('seed_023', '兵马俑',     '西安', '景点', 'seed'),
('seed_024', '回民街',     '西安', '景点', 'seed'),
('seed_025', '古城墙',     '西安', '景点', 'seed'),
('seed_026', '洱海',       '大理', '景点', 'seed'),
('seed_027', '大理古城',   '大理', '景点', 'seed'),
('seed_028', '苍山',       '大理', '景点', 'seed'),
('seed_029', '丽江古城',   '丽江', '景点', 'seed'),
('seed_030', '玉龙雪山',   '丽江', '景点', 'seed'),
('seed_031', '外滩',       '上海', '景点', 'seed'),
('seed_032', '东方明珠',   '上海', '景点', 'seed'),
('seed_033', '迪士尼乐园', '上海', '景点', 'seed'),
('seed_034', '城隍庙',     '上海', '景点', 'seed'),
('seed_035', '亚龙湾',     '三亚', '景点', 'seed'),
('seed_036', '天涯海角',   '三亚', '景点', 'seed'),
('seed_037', '蜈支洲岛',   '三亚', '景点', 'seed'),
('seed_038', '南山寺',     '三亚', '景点', 'seed'),
('seed_039', '九寨沟',     '阿坝', '景点', 'seed'),
('seed_040', '黄龙',       '阿坝', '景点', 'seed'),
('seed_041', '四姑娘山',   '阿坝', '景点', 'seed'),
('seed_042', '若尔盖草原', '阿坝', '景点', 'seed'),
('seed_043', '布达拉宫',   '拉萨', '景点', 'seed'),
('seed_044', '大昭寺',     '拉萨', '景点', 'seed'),
('seed_045', '青海湖',     '西宁', '景点', 'seed'),
('seed_046', '茶卡盐湖',   '西宁', '景点', 'seed'),
('seed_047', '龙门石窟',   '洛阳', '景点', 'seed'),
('seed_048', '白马寺',     '洛阳', '景点', 'seed'),
('seed_049', '黄鹤楼',     '武汉', '景点', 'seed'),
('seed_050', '东湖',       '武汉', '景点', 'seed'),
('seed_051', '橘子洲',     '长沙', '景点', 'seed'),
('seed_052', '岳麓山',     '长沙', '景点', 'seed'),
('seed_053', '中山陵',     '南京', '景点', 'seed'),
('seed_054', '夫子庙',     '南京', '景点', 'seed'),
('seed_055', '拙政园',     '苏州', '景点', 'seed'),
('seed_056', '虎丘',       '苏州', '景点', 'seed'),
('seed_057', '栈桥',       '青岛', '景点', 'seed'),
('seed_058', '崂山',       '青岛', '景点', 'seed'),
('seed_059', '星海广场',   '大连', '景点', 'seed'),
('seed_060', '老虎滩',     '大连', '景点', 'seed'),
('seed_061', '中央大街',   '哈尔滨','景点', 'seed'),
('seed_062', '冰雪大世界', '哈尔滨','景点', 'seed'),
('seed_063', '日月贝',     '珠海', '景点', 'seed'),
('seed_064', '长隆海洋王国','珠海', '景点', 'seed'),
('seed_065', '象鼻山',     '桂林', '景点', 'seed'),
('seed_066', '漓江',       '桂林', '景点', 'seed'),
('seed_067', '阳朔西街',   '桂林', '景点', 'seed'),
('seed_068', '银滩',       '北海', '景点', 'seed'),
('seed_069', '涠洲岛',     '北海', '景点', 'seed');

-- ============================================================
-- ✅ 建库完成
-- ============================================================
SELECT '拾路派数据库初始化完成！' AS message;
SELECT CONCAT('共创建 23 张表：') AS summary;
SELECT 'users, trip_plans, trip_days, trip_spots, trip_publish, comment' AS tables_1;
SELECT 'like_record, follow_relation, message, attractions, hotels, city' AS tables_2;
SELECT 'hiking_route, hiking_segment, user_points, point_record, sign_in_record' AS tables_3;
SELECT 'level_config, user_unlocked_template, admin_users, admin_logs' AS tables_4;
SELECT 'sensitive_words, system_announcements' AS tables_5;
SELECT '管理员账号: admin / admin123' AS admin_info;
