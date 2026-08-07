/**
 * 统一分享工具
 * 提供默认分享配置和常用分享模板
 * 所有页面统一引入，确保分享行为一致
 */

/**
 * 启用分享菜单（在页面 onLoad 中调用）
 * @param {string[]} [menus] - 分享菜单列表，默认 ['shareAppMessage', 'shareTimeline']
 */
function enableShareMenu(menus) {
  wx.showShareMenu({
    withShareTicket: true,
    menus: menus || ['shareAppMessage', 'shareTimeline']
  });
}

/**
 * 生成分享到朋友的配置
 * @param {Object} opts
 * @param {string} opts.title - 分享标题
 * @param {string} [opts.imageUrl] - 分享封面图（空字符串用默认）
 * @param {string} opts.path - 分享路径
 * @param {string} [opts.desc] - 分享描述（部分场景使用）
 */
function shareToFriend(opts) {
  return {
    title: opts.title || '拾路派 - AI 智能旅行规划',
    path: opts.path || '/pages/index/index',
    imageUrl: opts.imageUrl || ''
  };
}

/**
 * 生成分享到朋友圈的配置
 * @param {Object} opts
 * @param {string} opts.title - 分享标题
 * @param {string} [opts.imageUrl] - 分享封面图
 * @param {string} [opts.query] - 页面查询参数
 */
function shareToTimeline(opts) {
  return {
    title: opts.title || '拾路派 - 智能旅行规划',
    query: opts.query || '',
    imageUrl: opts.imageUrl || ''
  };
}

/**
 * 行程分享模板 — 给行程类页面使用
 * @param {Object} trip - 行程数据对象
 * @param {string} [fallbackTitle] - 无行程时的默认标题
 */
function shareTrip(trip, fallbackTitle) {
  if (!trip) {
    return shareToFriend({ title: fallbackTitle || '拾路派 - 发现精彩旅程' });
  }
  return shareToFriend({
    title: trip.name || trip.title || (trip.destination ? trip.destination + '之旅' : '') || '精彩旅程',
    imageUrl: trip.coverImage || trip.image || '',
    path: '/pages/trip-detail/trip-detail?publishId=' + (trip.publishId || trip.id || '')
  });
}

/**
 * 行程分享到朋友圈
 */
function shareTripToTimeline(trip, fallbackTitle) {
  if (!trip) {
    return shareToTimeline({ title: fallbackTitle || '拾路派 - 发现精彩旅程' });
  }
  return shareToTimeline({
    title: trip.name || trip.title || (trip.destination ? trip.destination + '之旅' : '') || '精彩旅程',
    imageUrl: trip.coverImage || trip.image || '',
    query: 'publishId=' + (trip.publishId || trip.id || '')
  });
}

module.exports = {
  enableShareMenu,
  shareToFriend,
  shareToTimeline,
  shareTrip,
  shareTripToTimeline
};
