const app = getApp();
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    faqList: [
      {
        q: '如何生成 AI 行程？',
        a: '在底部导航栏点击「行程」→ 「AI智能生成行程」，填写目的地、日期、偏好等信息，点击生成即可。AI 会为你规划每日行程和推荐酒店。'
      },
      {
        q: '如何发布行程到广场？',
        a: 'AI 生成行程后，在行程详情页底部点击「发布」按钮，选择封面图，即可将你的行程分享到发现广场。'
      },
      {
        q: '如何编辑已有行程？',
        a: '在「我的行程」列表中找到目标行程，点击「编辑」按钮，可以调整每日景点顺序、修改酒店信息等。'
      },
      {
        q: '如何同步到手机日历？',
        a: '在 AI 生成的行程结果页，点击底部工具栏的日历图标，一键将整个行程添加到手机系统日历。'
      },
      {
        q: '如何反馈问题或建议？',
        a: '你可以通过以下方式联系我们：\n邮箱：1414324637@qq.com\n在「消息」页面给我们留言'
      }
    ],
    expanded: {}
  },

  onLoad() {
    applyTheme(this);
    // 启用分享
    share.enableShareMenu();
  },

  onShow() {
    applyTheme(this);
  },

  toggleFaq(e) {
    const idx = e.currentTarget.dataset.index;
    const key = 'expanded[' + idx + ']';
    this.setData({ [key]: !this.data.expanded[idx] });
  },

  goBack() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile' }) });
  },

  // ==================== 分享 ====================

  onShareAppMessage() {
    return share.shareToFriend({
      title: '拾路派 - 帮助与反馈',
      path: '/pages/help/help'
    });
  },

  onShareTimeline() {
    return share.shareToTimeline({
      title: '拾路派 - 帮助与反馈'
    });
  }
});
