/**
 * 拾路派 — 本地缓存管理工具
 *
 * 设计原则：
 * 1. 先展示缓存 → 再异步拉取（"本地优先 + 服务端覆盖"模式）
 * 2. 所有缓存带时间戳，支持 TTL 过期
 * 3. Meta Key 记录所有缓存键，方便一键清零
 * 4. 清除缓存时保留个人信息（userInfo/token/preferences 等）
 */

// ═══════════════════════════════════════
// 数据缓存键名（页面级数据，可清除）
// ═══════════════════════════════════════
const DATA_KEYS = [
  'cache_im_messages_',    // 私信聊天记录，key 后缀为 targetUserId
  'cache_im_conversations', // 会话列表
  'cache_im_unread',        // IM 未读数
  'cache_user_profile',     // 当前用户资料
  'cache_user_settings',    // 用户设置（通知等）
  'cache_user_stats',       // 当前用户统计
  'cache_city_data',        // 城市分组数据
  'cache_hot_tags',         // 热门标签
  'cache_index_trips_p1',   // 首页行程第一页
  'cache_my_trips_p1',      // 我的行程第一页
  'cache_favorites_p1',     // 收藏第一页
  'cache_trip_detail_',     // 行程详情缓存
];

// ═══════════════════════════════════════
// 永久保留的键（个人信息，不清除）
// ═══════════════════════════════════════
const PERSISTENT_KEYS = [
  'userInfo',
  'token',
  'userId',
  'phone',
  'preferences',
  'darkMode',
];

/** 获取缓存 */
function get(key, ttlMs) {
  try {
    const raw = wx.getStorageSync('cache_' + key);
    if (!raw) return null;
    const entry = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (ttlMs && entry._ts) {
      if (Date.now() - entry._ts > ttlMs) {
        wx.removeStorageSync('cache_' + key);
        return null;
      }
    }
    return entry._data !== undefined ? entry._data : entry;
  } catch (e) {
    return null;
  }
}

/** 写入缓存 */
function set(key, data) {
  try {
    wx.setStorageSync('cache_' + key, {
      _ts: Date.now(),
      _data: data
    });
  } catch (e) {
    // 存储空间不足时静默跳过
    console.warn('[cache] 写入失败:', key, e);
  }
}

/** 删除指定缓存 */
function remove(key) {
  try {
    wx.removeStorageSync('cache_' + key);
  } catch (e) {
    // ignore
  }
}

/** 清除所有数据缓存（保留个人信息） */
function clearAll() {
  try {
    const info = wx.getStorageInfoSync();
    const keys = info.keys || [];
    let removedCount = 0;

    keys.forEach(key => {
      // 永久保留
      if (PERSISTENT_KEYS.includes(key)) return;

      // 以 cache_ 开头的缓存数据
      if (key.startsWith('cache_')) {
        wx.removeStorageSync(key);
        removedCount++;
      }
    });

    return { success: true, count: removedCount };
  } catch (e) {
    return { success: false, count: 0 };
  }
}

/** 获取缓存信息（占用空间） */
function getCacheInfo() {
  try {
    const info = wx.getStorageInfoSync();
    const keys = info.keys || [];
    let cacheKeys = [];
    let cacheSize = 0;

    keys.forEach(key => {
      if (key.startsWith('cache_') || (!PERSISTENT_KEYS.includes(key) && !key.startsWith('cache_'))) {
        // 计算非永久键的大小
      }
      if (key.startsWith('cache_')) {
        cacheKeys.push(key);
      }
    });

    // currentSize 是总存储大小，但无法精确分配
    // 用缓存键数量估算
    return {
      cacheKeys: cacheKeys,
      count: cacheKeys.length
    };
  } catch (e) {
    return { cacheKeys: [], count: 0 };
  }
}

module.exports = {
  get,
  set,
  remove,
  clearAll,
  getCacheInfo,
  DATA_KEYS,
  PERSISTENT_KEYS
};
