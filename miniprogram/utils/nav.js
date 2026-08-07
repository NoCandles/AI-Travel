/**
 * 统一页面跳转工具
 * 解决 navigateBack 回退策略不一致的问题
 */

/**
 * 智能返回：先尝试 navigateBack，失败则根据来源跳 Tab
 * @param {Object} options 
 *   - failTab: 失败时切到的 Tab 路径，默认 '/pages/index/index'
 *   - delta: 返回的页面数，默认 1
 */
function goBack(options = {}) {
  const { failTab = '/pages/index/index', delta = 1 } = options;
  wx.navigateBack({
    delta,
    fail: () => {
      // 无法返回时切到指定 Tab
      wx.switchTab({ url: failTab });
    }
  });
}

/**
 * 跳转到 Tab 页（封装 wx.switchTab）
 */
function switchTab(url) {
  wx.switchTab({ url });
}

/**
 * 重启到指定页面（清空页面栈）
 */
function reLaunch(url) {
  wx.reLaunch({ url });
}

/**
 * 跳转到非 Tab 页面（封装 wx.navigateTo）
 */
function navigateTo(url, options = {}) {
  wx.navigateTo({ url, ...options });
}

/**
 * 带表单保护的返回：有未保存内容时先提示
 * @param {boolean} hasUnsaved 是否有未保存内容
 * @param {Function} onConfirm 用户确认后执行
 * @param {Object} options goBack 的选项
 */
function goBackWithGuard(hasUnsaved, onConfirm, options = {}) {
  if (hasUnsaved) {
    wx.showModal({
      title: '提示',
      content: '有未保存的内容，确定要返回吗？',
      success: (res) => {
        if (res.confirm) {
          if (onConfirm) onConfirm();
          else goBack(options);
        }
      }
    });
  } else {
    goBack(options);
  }
}

module.exports = { goBack, switchTab, reLaunch, navigateTo, goBackWithGuard };
