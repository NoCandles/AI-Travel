/**
 * 安全导航工具
 * 自动检查页面栈深度，超过上限时使用 redirectTo 避免栈溢出
 */
const MAX_STACK_DEPTH = 9;

function safeNavigateTo(options) {
  const pages = getCurrentPages();
  const url = typeof options === 'string' ? options : options.url;

  if (pages.length >= MAX_STACK_DEPTH) {
    return wx.redirectTo(typeof options === 'string' ? { url } : { ...options });
  }
  return wx.navigateTo(typeof options === 'string' ? { url } : options);
}

function safeNavigateBack(delta = 1) {
  const pages = getCurrentPages();
  if (pages.length <= delta) {
    return wx.reLaunch({ url: '/pages/index/index' });
  }
  return wx.navigateBack({ delta });
}

module.exports = {
  safeNavigateTo,
  safeNavigateBack
};
