const api = require('../../utils/api');
const app = getApp();
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');
const auth = require('../../utils/auth');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    loading: true,
    publishList: []
  },

  onLoad() {
    if (!auth.checkLogin()) return;
    applyTheme(this);
    this.loadPublishList();
    // 启用分享
    share.enableShareMenu();
  },

  loadPublishList() {
    this.setData({ loading: true });
    const userId = wx.getStorageSync('userId') || '';
    api.getUserPublish(userId, userId)
      .then((list) => {
        this.setData({ publishList: list || [], loading: false });
      })
      .catch(() => {
        this.setData({ publishList: [], loading: false });
      });
  },

  viewDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/trip-detail/trip-detail?publishId=${id}`
    });
  },

  editPublish(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/planner/planner?publishId=${id}&tab=publish`
    });
  },

  deletePublish(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这条发布吗？',
      success: (res) => {
        if (res.confirm) {
          api.deletePublish(id)
            .then(() => {
              const list = this.data.publishList.filter(item => item.id !== id);
              this.setData({ publishList: list });
            })
            .catch(() => {
              wx.showToast({ title: '删除失败', icon: 'none' });
            });
        }
      }
    });
  },

  onPullDownRefresh() {
    this.loadPublishList();
    wx.stopPullDownRefresh();
  },

  // ==================== 分享 ====================

  onShareAppMessage(e) {
    // e.target.dataset 来自 open-type="share" 按钮
    const target = e && e.target && e.target.dataset;
    if (target && target.id) {
      return share.shareToFriend({
        title: target.title || '我的行程发布 - 拾路派',
        imageUrl: target.cover || '',
        path: '/pages/trip-detail/trip-detail?publishId=' + target.id
      });
    }
    return share.shareToFriend({
      title: '拾路派 - 我的发布',
      path: '/pages/my-publish/my-publish'
    });
  },

  onShareTimeline() {
    return share.shareToTimeline({
      title: '拾路派 - 我的发布'
    });
  }
});
