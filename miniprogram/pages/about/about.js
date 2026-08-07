const app = getApp();
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    infoList: [
      { label: '应用名称', value: '拾路派 PathFinder' },
      { label: '应用版本', value: 'v1.0.0' },
      { label: '开发团队', value: '拾路派团队' },
      { label: '联系我们', value: '1414324637@qq.com' }
    ]
  },

  onLoad() {
    applyTheme(this);
    // 启用分享
    share.enableShareMenu();
  },

  onShow() {
    applyTheme(this);
  },

  goBack() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile' }) });
  },

  // ==================== 分享 ====================

  onShareAppMessage() {
    return share.shareToFriend({
      title: '拾路派 - 智能旅行规划',
      path: '/pages/about/about'
    });
  },

  onShareTimeline() {
    return share.shareToTimeline({
      title: '拾路派 - 智能旅行规划'
    });
  }
});
