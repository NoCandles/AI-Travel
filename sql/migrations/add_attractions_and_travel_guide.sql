-- ============================================
-- 拾路派 — 数据库补全脚本
-- 执行日期：2026-07-03
-- 说明：补全 attractions 表 + trip_spots.travel_guide 字段
-- ============================================

-- ════════════════════════════════════════════
-- 1. 景点缓存表 attractions
--    ChatServiceImpl 加载景点→城市映射依赖此表
--    AttractionServiceImpl 缓存腾讯地图POI搜索结果依赖此表
-- ════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `attractions` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `attraction_id` VARCHAR(64) DEFAULT NULL COMMENT '腾讯地图POI ID',
    `name`         VARCHAR(200) DEFAULT NULL COMMENT '景点名称',
    `address`      VARCHAR(500) DEFAULT NULL COMMENT '地址',
    `tel`          VARCHAR(50)  DEFAULT NULL COMMENT '电话',
    `latitude`     DOUBLE       DEFAULT NULL COMMENT '纬度',
    `longitude`    DOUBLE       DEFAULT NULL COMMENT '经度',
    `distance`     INT          DEFAULT NULL COMMENT '距离(米)',
    `city`         VARCHAR(50)  DEFAULT NULL COMMENT '所属城市',
    `category`     VARCHAR(200) DEFAULT NULL COMMENT 'POI分类',
    `tags`         VARCHAR(500) DEFAULT NULL COMMENT '标签',
    `source`       VARCHAR(50)  DEFAULT NULL COMMENT '数据来源',
    `search_lat`   DOUBLE       DEFAULT NULL COMMENT '搜索时中心纬度',
    `search_lng`   DOUBLE       DEFAULT NULL COMMENT '搜索时中心经度',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_attraction_id` (`attraction_id`),
    KEY `idx_city` (`city`),
    KEY `idx_search_lat_lng` (`search_lat`, `search_lng`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景点缓存表';

-- ════════════════════════════════════════════
-- 2. trip_spots 表添加 travel_guide 字段
--    TripSpot 实体已有 travelGuide 字段，对应此列
--    用于存储 AI 生成的景点游玩推荐/攻略
-- ════════════════════════════════════════════
ALTER TABLE `trip_spots`
ADD COLUMN IF NOT EXISTS `travel_guide` TEXT DEFAULT NULL COMMENT '游玩推荐/攻略' AFTER `tips`;

-- 如果上面的 IF NOT EXISTS 语法报错，用下面这条（先注释上面那条）：
-- ALTER TABLE `trip_spots` ADD COLUMN `travel_guide` TEXT DEFAULT NULL COMMENT '游玩推荐/攻略' AFTER `tips`;

-- ════════════════════════════════════════════
-- 3. 插入一些常用城市的景点种子数据（可选）
--    让 ChatService 的景点→城市映射能立即生效
-- ════════════════════════════════════════════
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
('seed_063', '中山路步行街','厦门', '景点', 'seed'),
('seed_064', '日月贝',     '珠海', '景点', 'seed'),
('seed_065', '长隆海洋王国','珠海', '景点', 'seed'),
('seed_066', '象鼻山',     '桂林', '景点', 'seed'),
('seed_067', '漓江',       '桂林', '景点', 'seed'),
('seed_068', '阳朔西街',   '桂林', '景点', 'seed'),
('seed_069', '银滩',       '北海', '景点', 'seed'),
('seed_070', '涠洲岛',     '北海', '景点', 'seed');
