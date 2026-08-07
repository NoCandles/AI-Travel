/**
 * 全局配置常量
 * 所有硬编码值集中管理，切换环境只需改 DEBUG_MODE
 */
module.exports = {
  // ========== 环境 ==========
  DEBUG_MODE: false,                          // true: localhost, false: 云托管
  CLOUD_ENV_ID: 'prod-d5gpx3d1m26d36f87',    // 微信云托管环境 ID
  CLOUD_SERVICE_NAME: 'springboot-6ccw',      // 云托管服务名（X-WX-SERVICE）

  // 本地调试地址（DEBUG_MODE=true 时使用）
  LOCAL_BASE_URL: 'http://localhost:8080',
  LOCAL_WS_BASE_URL: 'ws://localhost:8080',

  // 云托管地址（DEBUG_MODE=false 时使用）
  CLOUD_BASE_URL: 'https://springboot-6ccw-264076-8-1407611099.sh.run.tcloudbase.com',
  // 云端 WebSocket 由 wx.cloud.connectContainer 根据云环境与服务名路由，
  // 不再依赖易过期的静态公网域名；字段仅为兼容 globalData 保留。
  CLOUD_WS_BASE_URL: '',

  // 云托管 H5 导航中转地址（供外部浏览器打开地图等）
  NAV_BRIDGE_HOST: 'https://springboot-6ccw-264076-8-1407611099.sh.run.tcloudbase.com',

  // ========== 超时 ==========
  POLL_TIMEOUT: 30000,        // AI 生成轮询超时 ms
  HEARTBEAT_INTERVAL: 10000,  // WebSocket 心跳间隔 ms
  REQUEST_TIMEOUT: 15000,     // HTTP 请求默认超时 ms

  // ========== 分页 ==========
  PAGE_SIZE: 20,
  MAX_STACK_DEPTH: 9,

  // ========== 功能开关 ==========
  POINTS_ENABLED: false,
  DARK_MODE_DEFAULT: false,

  // ========== 限制 ==========
  MAX_CHAT_MESSAGES: 500,     // 聊天消息缓存上限
  CHAT_CLEANUP_KEEP: 200,     // 超上限时保留最近条数

  // ========== 城市数据 ==========
  CITIES: [
    { keys: ['厦门'], name: '厦门' },
    { keys: ['成都', '蓉城', '都江堰', '青城山'], name: '成都' },
    { keys: ['北京', '首都'], name: '北京' },
    { keys: ['大理', '洱海'], name: '大理' },
    { keys: ['西安', '长安'], name: '西安' },
    { keys: ['杭州', '西湖'], name: '杭州' },
    { keys: ['重庆', '山城'], name: '重庆' },
    { keys: ['三亚', '天涯'], name: '三亚' },
    { keys: ['丽江', '古城'], name: '丽江' },
    { keys: ['上海', '魔都'], name: '上海' },
    { keys: ['阿坝', '九寨沟', '黄龙', '若尔盖', '四姑娘山'], name: '阿坝' },
    { keys: ['拉萨', '西藏'], name: '拉萨' },
    { keys: ['青海湖', '西宁'], name: '西宁' },
    { keys: ['桂林', '阳朔'], name: '桂林' },
    { keys: ['北海', '涠洲岛'], name: '北海' },
    { keys: ['广州'], name: '广州' },
    { keys: ['深圳'], name: '深圳' },
    { keys: ['武汉'], name: '武汉' },
    { keys: ['长沙'], name: '长沙' },
    { keys: ['南京'], name: '南京' },
    { keys: ['苏州'], name: '苏州' },
    { keys: ['青岛'], name: '青岛' },
    { keys: ['大连'], name: '大连' },
    { keys: ['哈尔滨'], name: '哈尔滨' },
    { keys: ['兰州'], name: '兰州' },
    { keys: ['银川'], name: '银川' },
    { keys: ['呼和浩特'], name: '呼和浩特' }
  ],

  // ========== 景点数据 ==========
  KNOWN_SPOTS: [
    '宽窄巷子','锦里','武侯祠','大熊猫基地','太古里','春熙路','都江堰','青城山','九寨沟',
    '鼓浪屿','曾厝垵','南普陀寺','中山路','环岛路','沙坡尾','厦门大学',
    '故宫','天安门','长城','颐和园','天坛','南锣鼓巷','798','三里屯',
    '洱海','苍山','双廊','喜洲','三塔','蝴蝶泉',
    '兵马俑','大雁塔','回民街','城墙','华清池',
    '西湖','灵隐寺','雷峰塔','断桥','龙井村'
  ]
};
