/**
 * 轻量行为埋点缓冲区。
 *
 * 在尚未接入专用分析服务时，先将关键漏斗事件保存在本地；后续可由
 * 后端批量消费或替换 track 实现，不影响页面调用方。
 */
const STORAGE_KEY = 'analytics_event_buffer';
const MAX_EVENTS = 100;

function track(event, properties = {}) {
  const item = {
    event,
    properties,
    timestamp: Date.now()
  };
  try {
    const events = wx.getStorageSync(STORAGE_KEY) || [];
    events.push(item);
    wx.setStorageSync(STORAGE_KEY, events.slice(-MAX_EVENTS));
  } catch (err) {
    // 埋点不得影响主流程。
    console.warn('[analytics] track failed', err);
  }
  console.log('[analytics]', item);
}

function getBufferedEvents() {
  try {
    return wx.getStorageSync(STORAGE_KEY) || [];
  } catch (err) {
    return [];
  }
}

module.exports = { track, getBufferedEvents };
