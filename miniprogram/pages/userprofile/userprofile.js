const app = getApp();
const api = require('../../utils/api');
const socialApi = require('../../api/social');
const { applyTheme } = require('../../utils/theme');
const auth = require('../../utils/auth');

function resolveAssetUrl(url) {
  if (!url) return '';
  if (/^(https?:\/\/|cloud:\/\/|wxfile:\/\/|http:\/\/tmp\/)/.test(url)) return url;
  const C = require('../../config/constants');
  return C.CLOUD_BASE_URL + url;
}

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    userId: '',
    profile: {},
    stats: {},
    routes: [],
    coverImage: '',
    shortId: '',
    isSelf: false,
    isFollowed: false,
    loading: true
  },

  onLoad(options) {
    applyTheme(this);
    const userId = options && options.userId ? options.userId : '';
    this.setData({ userId });
    if (!userId) {
      wx.showToast({ title: '用户不存在', icon: 'none' });
      return;
    }
    this.loadPageData();
  },

  onShow() {
    applyTheme(this);
  },

  loadPageData() {
    this.setData({ loading: true });
    Promise.all([
      api.user.getPublicUserProfile(this.data.userId),
      socialApi.getUserPublish(this.data.userId, wx.getStorageSync('userId') || '')
    ])
      .then(([profileData, routes]) => {
        const profile = (profileData && profileData.profile) || {};
        this.setData({
          profile: {
            ...profile,
            avatar: resolveAssetUrl(profile.avatar)
          },
          coverImage: resolveAssetUrl(profile.coverImage),
          shortId: profile.id ? profile.id.slice(0, 8).toUpperCase() : '',
          stats: (profileData && profileData.stats) || {},
          isSelf: !!(profileData && profileData.isSelf),
          isFollowed: !!(profileData && profileData.isFollowed),
          routes: (routes || []).map(item => ({
            ...item,
            coverImage: resolveAssetUrl(item.coverImage)
          })),
          loading: false
        });
        if (profileData && profileData.isSelf) {
          wx.switchTab({ url: '/pages/profile/profile' });
        }
      })
      .catch((err) => {
        console.error('加载他人主页失败:', err);
        this.setData({ loading: false });
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  toggleFollow() {
    if (!auth.checkLogin()) return;
    const isFollowed = this.data.isFollowed;
    this.setData({ isFollowed: !isFollowed });
    const action = isFollowed ? socialApi.unfollowUser(this.data.userId) : socialApi.followUser(this.data.userId);
    action
      .then(() => {
        const followerCount = (this.data.stats.followerCount || 0) + (isFollowed ? -1 : 1);
        this.setData({
          'stats.followerCount': Math.max(0, followerCount)
        });
      })
      .catch(() => {
        this.setData({ isFollowed });
        wx.showToast({ title: '操作失败', icon: 'none' });
      });
  },

  sendMessage() {
    if (!auth.checkLogin()) return;
    wx.navigateTo({ url: '/pages/imchat/imchat?targetUserId=' + this.data.userId });
  },

  viewRouteDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/trip-detail/trip-detail?publishId=' + id });
  },

  viewFollowing() {
    wx.navigateTo({ url: '/pages/following/following?userId=' + this.data.userId });
  },

  viewFollowers() {
    wx.navigateTo({ url: '/pages/following/following?userId=' + this.data.userId + '&type=followers' });
  }
});
