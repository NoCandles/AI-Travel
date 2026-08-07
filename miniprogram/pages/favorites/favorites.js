const app = getApp();
const api = require('../../utils/api');
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');
const auth = require('../../utils/auth');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    loading: true,
    loadingMore: false,
    favorites: [],
    page: 1,
    hasMore: true,
    empty: false,
    emptyText: '还没有收藏\n在行程详情页点击星星收藏你喜欢的行程'
  },

  onLoad() {
    if (!auth.checkLogin()) return;
    applyTheme(this);
    share.enableShareMenu();
  },

  onShow() {
    applyTheme(this);
    this.setData({ page: 1, hasMore: true });
    this.loadFavorites();
  },

  loadFavorites() {
    const { page } = this.data;
    this.setData(page === 1 ? { loading: true } : { loadingMore: true });
    return api.getFavorites(page, 20)
      .then((list) => {
        const nextList = this.normalizeFavorites(list || []);
        const favorites = page === 1 ? nextList : [...this.data.favorites, ...nextList];
        this.setData({ favorites, loading: false, loadingMore: false, hasMore: (list || []).length === 20, empty: favorites.length === 0 });
      })
      .catch(() => {
        this.setData({ loading: false, loadingMore: false });
      });
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loadingMore) return;
    this.setData({ page: this.data.page + 1 }, () => this.loadFavorites());
  },

  onPullDownRefresh() {
    this.setData({ page: 1, hasMore: true });
    this.loadFavorites().finally(() => wx.stopPullDownRefresh());
  },

  normalizeFavorites(list) {
    return list.map((item) => {
      const detailId = item.id || item.publishId || item.targetId || '';
      const location = item.location || item.destination || '未知目的地';
      const title = item.title || item.name || (location ? location + '之旅' : '收藏行程');
      return {
        ...item,
        detailId,
        title,
        location,
        days: item.days || 1,
        nights: item.nights != null ? item.nights : Math.max(0, (item.days || 1) - 1),
        coverImage: item.coverImage || item.image || item.imageUrl || '',
        nickname: item.nickname || item.userName || '旅行家',
        avatar: item.avatar || item.avatarUrl || '',
        likeCount: item.likeCount || 0,
        commentCount: item.commentCount || 0,
        favCount: item.favCount || 0
      };
    });
  },

  viewDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/trip-detail/trip-detail?id=' + id });
  },

  onShareAppMessage(e) {
    const { title, cover } = e.target.dataset;
    return { title: title || '拾路派旅行', imageUrl: cover };
  }
});
