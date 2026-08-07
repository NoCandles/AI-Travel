/**
 * 网络状态管理器
 * 全局监听网络状态变化，提供统一 API 供各模块查询
 */

let _isOnline = true;
let _networkType = 'wifi';
const _listeners = [];

/**
 * 初始化网络状态监听（在 app.js 中调用一次）
 */
function init() {
  // 先获取当前网络状态
  wx.getNetworkType({
    success(res) {
      _isOnline = res.networkType !== 'none';
      _networkType = res.networkType;
    }
  });

  // 监听网络状态变化
  wx.onNetworkStatusChange((res) => {
    const wasOnline = _isOnline;
    _isOnline = res.isConnected;
    _networkType = res.networkType;

    if (wasOnline && !_isOnline) {
      console.warn('[网络] 已断开');
      wx.showToast({ title: '网络已断开', icon: 'none', duration: 2000 });
    } else if (!wasOnline && _isOnline) {
      wx.showToast({ title: '网络已恢复', icon: 'success', duration: 1500 });
    }

    // 通知所有监听器
    _listeners.forEach(fn => {
      try { fn(_isOnline, _networkType); } catch (e) { console.warn('[网络] 监听器错误:', e); }
    });
  });
}

/**
 * 当前是否在线
 * @returns {boolean}
 */
function isOnline() {
  return _isOnline;
}

/**
 * 获取当前网络类型
 * @returns {string} 'wifi' | '4g' | '3g' | '2g' | 'unknown' | 'none'
 */
function getNetworkType() {
  return _networkType;
}

/**
 * 注册网络状态变化监听器
 * @param {Function} callback 回调 (isOnline, networkType) => void
 * @returns {Function} 取消注册的函数
 */
function onNetworkChange(callback) {
  _listeners.push(callback);
  return () => {
    const idx = _listeners.indexOf(callback);
    if (idx !== -1) _listeners.splice(idx, 1);
  };
}

module.exports = { init, isOnline, getNetworkType, onNetworkChange };
