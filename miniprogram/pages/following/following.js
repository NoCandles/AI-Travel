const app = getApp();
const api = require('../../utils/api');
const { applyTheme } = require('../../utils/theme');
const auth = require('../../utils/auth');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    loading: true,
    followingList: [],
    currentUserId: null,
    selfUserId: '',
    listType: 'following',
    pageTitle: '我的关注',
    empty: false,
    emptyText: '还没有关注\n去发现页探索更多有趣的旅行家吧'
  },

  onLoad(options) {
    if (!auth.checkLogin()) return;
    applyTheme(this);
    const currentUserId = options.userId ||
      (app.globalData.userInfo && app.globalData.userInfo.id) ||
      wx.getStorageSync('userId') ||
      '';
    const listType = options.type === 'followers' ? 'followers' : 'following';
    this.setData({
      currentUserId,
      selfUserId: (app.globalData.userInfo && app.globalData.userInfo.id) || wx.getStorageSync('userId') || '',
      listType,
      pageTitle: listType === 'followers' ? '我的粉丝' : '我的关注',
      emptyText: listType === 'followers'
        ? '还没有粉丝\n多分享精彩路线，会被更多旅行家看见'
        : '还没有关注\n去发现页探索更多有趣的旅行家吧'
    });
    this._firstShow = true;
    this.loadFollowingList();
  },

  onShow() {
    applyTheme(this);
    if (this._firstShow) {
      this._firstShow = false;
      return;
    }
    this.loadFollowingList();
  },

  loadFollowingList() {
    if (!this.data.currentUserId) {
      this.setData({ loading: false, empty: true });
      return;
    }

    this.setData({ loading: true });

    api.getFollowingList(this.data.currentUserId, this.data.listType)
      .then((res) => {
        const rawList = Array.isArray(res)
          ? res
          : (Array.isArray(res && res.records) ? res.records : (Array.isArray(res && res.list) ? res.list : []));
        const filteredList = rawList
          .map(item => this.normalizeUser(item))
          .filter(item => item.id && item.id !== this.data.currentUserId);
        this.setData({
          followingList: filteredList,
          loading: false,
          empty: filteredList.length === 0
        });
      })
      .catch((err) => {
        console.error('加载关注列表失败:', err);
        this.setData({
          followingList: [],
          loading: false,
          empty: true
        });
        wx.showToast({
          title: '加载失败',
          icon: 'none'
        });
      });
  },

  normalizeUser(item) {
    const id = item.id || item.userId || item.followingId || '';
    return {
      ...item,
      id,
      nickname: item.nickname || item.name || item.nickName || '未知用户',
      avatar: item.avatar || item.avatarUrl || '/images/icons/user.svg',
      signature: item.signature || item.bio || '还没有填写个人简介',
      city: item.city || '',
      followerCount: item.followerCount || 0,
      tripCount: item.tripCount || 0,
      followed: !!item.followed
    };
  },

  toggleFollow(e) {
    const userId = e.currentTarget.dataset.id;
    const followed = e.currentTarget.dataset.followed === true || e.currentTarget.dataset.followed === 'true';
    if (!userId) return;

    if (!followed) {
      api.followUser(userId)
        .then(() => {
          this.setData({
            followingList: this.data.followingList.map(item => (
              item.id === userId ? { ...item, followed: true } : item
            ))
          });
          app.globalData.statsDirty = true;
        })
        .catch((err) => {
          console.error('关注失败:', err);
          wx.showToast({ title: '操作失败', icon: 'none' });
        });
      return;
    }

    wx.showModal({
      title: '取消关注',
      content: '确定要取消关注该用户吗？',
      success: (res) => {
        if (res.confirm) {
          api.unfollowUser(userId)
            .then(() => {
              if (this.data.listType === 'following') {
                const newList = this.data.followingList.filter(item => item.id !== userId);
                this.setData({
                  followingList: newList,
                  empty: newList.length === 0
                });
              } else {
                this.setData({
                  followingList: this.data.followingList.map(item => (
                    item.id === userId ? { ...item, followed: false } : item
                  ))
                });
              }
              app.globalData.statsDirty = true;
            })
            .catch((err) => {
              console.error('取消关注失败:', err);
              wx.showToast({
                title: '操作失败',
                icon: 'none'
              });
            });
        }
      }
    });
  },

  viewUserProfile(e) {
    const userId = e.currentTarget.dataset.id;
    if (!userId) return;
    if (userId === this.data.selfUserId) {
      wx.switchTab({ url: '/pages/profile/profile' });
      return;
    }
    wx.navigateTo({ url: '/pages/userprofile/userprofile?userId=' + userId });
  },

  goToSquare() {
    wx.switchTab({
      url: '/pages/index/index'
    });
  },

  onPullDownRefresh() {
    this.loadFollowingList();
    wx.stopPullDownRefresh();
  }
});
