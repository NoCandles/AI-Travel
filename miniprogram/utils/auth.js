const app = getApp();

// 导入 API 函数
const authAPI = require('../api/auth');

/**
 * 检查用户是否已登录
 * @returns {boolean} 是否已登录
 */
function isLoggedIn() {
  return app.globalData.isLoggedIn && app.globalData.userInfo;
}

/**
 * 获取当前用户 ID
 * @returns {string} 用户 ID
 */
function getUserId() {
  const userId = wx.getStorageSync('userId');
  if (userId) {
    return userId;
  }
  
  // 如果没有 userId，生成一个临时的
  const tempUserId = 'guest_' + Date.now();
  wx.setStorageSync('userId', tempUserId);
  return tempUserId;
}

/**
 * 检查登录状态，如果未登录则跳转到登录页
 * @param {boolean} autoRedirect 是否自动跳转，默认 true
 * @returns {boolean} 是否已登录
 */
function checkLogin(autoRedirect = true) {
  if (isLoggedIn()) {
    return true;
  }
  
  if (autoRedirect) {
    wx.showModal({
      title: '提示',
      content: '请先登录',
      showCancel: false,
      confirmText: '去登录',
      success: () => {
        wx.navigateTo({
          url: '/pages/login/login'
        });
      }
    });
  } else {
    wx.showToast({
      title: '请先登录',
      icon: 'none'
    });
  }
  
  return false;
}

/**
 * 获取用户信息
 * @returns {object|null} 用户信息对象
 */
function getUserInfo() {
  return app.globalData.userInfo || null;
}

module.exports = {
  isLoggedIn,
  getUserId,
  checkLogin,
  getUserInfo,
  // 从 api/auth.js 导出登录相关接口
  loginByPhone: authAPI.loginByPhone,
  guestLogin: authAPI.guestLogin,
  resetPassword: authAPI.resetPassword,
  wechatPhoneLogin: authAPI.wechatPhoneLogin
};
