const app = getApp();
const api = require('../../utils/api');
const cache = require('../../utils/cache');
const { handleNotLogin } = require('../../utils/request');
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');
const { generateVisitedPolygons } = require('../../utils/cityBoundary');

function resolveAssetUrl(url) {
  if (!url) return '';
  if (/^(https?:\/\/|cloud:\/\/|wxfile:\/\/|http:\/\/tmp\/)/.test(url)) return url;
  const C = require('../../config/constants');
  return C.CLOUD_BASE_URL + url;
}

Page({
  data: {
    userInfo: null,
    isLoggedIn: false,
    userProfile: {},
    coverImage: '',
    shortId: '',
    stats: {
      tripCount: 0,
      publishCount: 0,
      favoriteCount: 0,
      followingCount: 0,
      followerCount: 0,
      totalLikes: 0,
      mutualFollowCount: 0
    },
    pointsInfo: {},
    levelInfo: {},
    pointsEnabled: app.globalData.pointsEnabled,
    darkMode: (app.globalData && app.globalData.darkMode) || false,
    contentTabs: [
      { key: 'favorites', text: '收藏' },
      { key: 'liked', text: '喜欢' },
      { key: 'visited', text: '去过' }
    ],
    activeContentTab: 'favorites',
    favoriteRoutes: [],
    likedRoutes: [],
    visitedCities: [],
    contentLoading: false,
    favoritePage: 1,
    likedPage: 1,
    favoriteHasMore: true,
    likedHasMore: true,
    visitedSummary: {
      totalCities: 0,
      totalTrips: 0
    },
    mapLatitude: 35.5,
    mapLongitude: 104.5,
    mapScale: 4,
    polygons: []
  },

  onLoad() {
    applyTheme(this);
    this.loadUserInfo();
    share.enableShareMenu();
  },

  onShow() {
    applyTheme(this);
    this.setData({
      manualDarkOverride: app.globalData.themeFollowSystem === false && app.globalData.darkMode === true,
      darkMode: app.globalData.darkMode
    });
    this.loadUserInfo();
    if (this.data.isLoggedIn) {
      // 先从缓存展示（即时渲染）
      const cachedProfile = cache.get('user_profile', 10 * 60 * 1000);
      if (cachedProfile) this._applyProfile(cachedProfile);
      const cachedStats = cache.get('user_stats', 5 * 60 * 1000);
      if (cachedStats) this._applyStats(cachedStats);
      // 然后从服务器拉取最新数据
      this.loadUserProfile();
      this.loadStats();
      this.refreshContentTab();
    }
  },

  onReachBottom() {
    const { activeContentTab } = this.data;
    if (activeContentTab === 'favorites' && this.data.favoriteHasMore && !this.data.contentLoading) {
      this.setData({ favoritePage: this.data.favoritePage + 1 }, () => this.loadFavorites(false));
    }
    if (activeContentTab === 'liked' && this.data.likedHasMore && !this.data.contentLoading) {
      this.setData({ likedPage: this.data.likedPage + 1 }, () => this.loadLikedRoutes(false));
    }
  },

  onPullDownRefresh() {
    this.loadStats();
    this.refreshContentTab().finally(() => wx.stopPullDownRefresh());
  },

  loadUserInfo() {
    const userInfo = app.globalData.userInfo;
    const isLoggedIn = app.globalData.isLoggedIn;
    this.setData({
      userInfo: userInfo || {},
      isLoggedIn,
      shortId: userInfo && userInfo.id ? userInfo.id.substring(0, 8) : ''
    });
  },

  loadUserProfile() {
    api.getUserProfile()
      .then((profile) => {
        if (!profile) return;
        // 写入缓存（保留原始数据）
        cache.set('user_profile', profile);
        this._applyProfile(profile);
      })
      .catch((err) => {
        console.warn('加载用户资料失败:', err);
        // 网络异常时尝试从缓存读取
        if (!this.data.userProfile.signature) {
          const cached = cache.get('user_profile', 10 * 60 * 1000);
          if (cached) this._applyProfile(cached);
        }
      });
  },

  /** 应用用户资料到页面数据 */
  _applyProfile(profile) {
    const C = require('../../config/constants');
    const useLocal = (app.globalData && app.globalData.debugMode) || false;
    const data = {
      'userProfile.signature': profile.signature || '',
      'userProfile.gender': profile.gender || 0,
      'userProfile.city': profile.city || '',
      'userProfile.province': profile.province || '',
      'userInfo.nickName': profile.nickname || this.data.userInfo.nickName,
      'userInfo.avatarUrl': profile.avatar || this.data.userInfo.avatarUrl
    };
    if (profile.coverImage) data.coverImage = useLocal ? profile.coverImage : resolveAssetUrl(profile.coverImage);
    this.setData(data);
  },

  loadStats() {
    if (!this.data.isLoggedIn) return;
    api.getUserStats()
      .then((stats) => {
        cache.set('user_stats', stats);
        this._applyStats(stats);
      })
      .catch((err) => {
        console.error('加载统计数据失败:', err);
        handleNotLogin(err);
        // 网络异常时尝试从缓存读取（5 分钟有效）
        const cached = cache.get('user_stats', 5 * 60 * 1000);
        if (cached) this._applyStats(cached);
      });
  },

  /** 应用统计数据到页面 */
  _applyStats(stats) {
    const data = {
      'stats.tripCount': stats.tripCount || 0,
      'stats.publishCount': stats.publishCount || 0,
      'stats.favoriteCount': stats.favoriteCount || 0,
      'stats.followingCount': stats.followingCount || 0,
      'stats.followerCount': stats.followerCount || 0,
      'stats.totalLikes': stats.totalLikes || 0,
      'stats.mutualFollowCount': stats.mutualFollowCount || 0
    };
    if (this.data.pointsEnabled) {
      data['pointsInfo.availablePoints'] = stats.availablePoints || 0;
      data['pointsInfo.totalPoints'] = stats.totalPoints || 0;
      data['pointsInfo.usedPoints'] = stats.usedPoints || 0;
      data['pointsInfo.signInStreak'] = stats.signInStreak || 0;
      data['levelInfo.level'] = stats.level || 1;
      data['levelInfo.title'] = stats.levelTitle || '旅游新手';
    }
    this.setData(data);
  },

  switchContentTab(e) {
    const activeContentTab = e.currentTarget.dataset.tab;
    if (activeContentTab === this.data.activeContentTab) return;
    this.setData({ activeContentTab }, () => this.loadActiveContentTab());
  },

  refreshContentTab() {
    const tab = this.data.activeContentTab;
    if (tab === 'favorites') {
      this.setData({ favoritePage: 1, favoriteHasMore: true });
      return this.loadFavorites(true);
    }
    if (tab === 'liked') {
      this.setData({ likedPage: 1, likedHasMore: true });
      return this.loadLikedRoutes(true);
    }
    return this.loadVisitedCities();
  },

  loadActiveContentTab() {
    const tab = this.data.activeContentTab;
    if (tab === 'favorites' && this.data.favoriteRoutes.length === 0) return this.loadFavorites(true);
    if (tab === 'liked' && this.data.likedRoutes.length === 0) return this.loadLikedRoutes(true);
    if (tab === 'visited' && this.data.visitedCities.length === 0) return this.loadVisitedCities();
    return Promise.resolve();
  },

  loadFavorites(reset) {
    const page = reset ? 1 : this.data.favoritePage;
    this.setData({ contentLoading: true });
    return api.getFavorites(page, 10)
      .then((list) => {
        const nextList = this.normalizeRoutes(list || []);
        this.setData({
          favoriteRoutes: reset ? nextList : this.data.favoriteRoutes.concat(nextList),
          favoritePage: page,
          favoriteHasMore: (list || []).length === 10,
          contentLoading: false
        });
      })
      .catch((err) => {
        this.setData({ contentLoading: false });
        handleNotLogin(err);
      });
  },

  loadLikedRoutes(reset) {
    const page = reset ? 1 : this.data.likedPage;
    this.setData({ contentLoading: true });
    return api.getLikedRoutes(page, 10)
      .then((list) => {
        const nextList = this.normalizeRoutes(list || []);
        this.setData({
          likedRoutes: reset ? nextList : this.data.likedRoutes.concat(nextList),
          likedPage: page,
          likedHasMore: (list || []).length === 10,
          contentLoading: false
        });
      })
      .catch((err) => {
        this.setData({ contentLoading: false });
        handleNotLogin(err);
      });
  },

  loadVisitedCities() {
    this.setData({ contentLoading: true });
    return Promise.all([
      api.getFootprintDetail(),
      api.getCities()
    ])
      .then(([footprintList, allCitiesList]) => {
        const visitedMap = {};
        let totalTrips = 0;
        (footprintList || []).forEach((item) => {
          const name = item.destination || item.name || item;
          const tripCount = item.tripCount || 1;
          if (!name) return;
          totalTrips += tripCount;
          visitedMap[name] = (visitedMap[name] || 0) + tripCount;
        });
        const visitedCities = Object.keys(visitedMap).map((name, index) => ({
            name,
            tripCount: visitedMap[name],
            rank: index + 1
        }));
        const mapCities = (allCitiesList || [])
          .filter((city) => visitedMap[city.name] && city.lng && city.lat)
          .map((city) => ({
            ...city,
            tripCount: visitedMap[city.name]
          }));
        const polygons = generateVisitedPolygons(mapCities);
        this.setData({
          visitedCities,
          polygons,
          visitedSummary: {
            totalCities: visitedCities.length,
            totalTrips
          },
          contentLoading: false
        });
      })
      .catch((err) => {
        console.error('加载去过城市失败:', err);
        this.setData({ visitedCities: [], polygons: [], contentLoading: false });
      });
  },

  normalizeRoutes(list) {
    return list.map((item) => {
      const detailId = item.id || item.publishId || item.targetId || '';
      const location = item.location || item.destination || '未知目的地';
      return {
        ...item,
        detailId,
        title: item.title || item.name || (location ? location + '之旅' : '旅行路线'),
        location,
        days: item.days || 1,
        nights: item.nights != null ? item.nights : Math.max(0, (item.days || 1) - 1),
        coverImage: item.coverImage || item.image || item.imageUrl || '/images/default-trip.jpg',
        nickname: item.nickname || item.userName || '旅行家',
        avatar: item.avatar || item.avatarUrl || '/images/icons/user.svg',
        likeCount: item.likeCount || 0,
        favCount: item.favCount || 0
      };
    });
  },

  viewRouteDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/trip-detail/trip-detail?id=' + id });
  },

  noop() {},

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' });
  },

  editProfile() {
    wx.navigateTo({ url: '/pages/edit-profile/edit-profile' });
  },

  viewFollowing() {
    const userId = app.globalData.userInfo && app.globalData.userInfo.id;
    wx.navigateTo({ url: `/pages/following/following?userId=${userId}` });
  },

  viewFollowers() {
    const userId = app.globalData.userInfo && app.globalData.userInfo.id;
    wx.navigateTo({
      url: `/pages/following/following?userId=${userId}&type=followers`
    });
  },

  goSettings() {
    wx.navigateTo({ url: '/pages/settings/settings' });
  },

  onChangeCover() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        this.uploadCover(res.tempFiles[0].tempFilePath);
      },
      fail: () => {}
    });
  },

  uploadCover(filePath) {
    const C = require('../../config/constants');
    const useLocal = (app.globalData && app.globalData.debugMode) || false;
    if (!useLocal) {
      const ext = (filePath.split('.').pop() || 'jpg').toLowerCase();
      const cloudPath = `covers/${Date.now()}_${Math.random().toString(36).slice(2, 10)}.${ext}`;
      wx.cloud.uploadFile({
        cloudPath,
        filePath,
        success: (uploadRes) => {
          const coverUrl = uploadRes.fileID;
          api.updateCoverImage(coverUrl)
            .then(() => {
              this.setData({ coverImage: coverUrl });
            })
            .catch((err) => {
              console.error('保存封面失败:', err);
              wx.showToast({ title: '封面保存失败', icon: 'none' });
            });
        },
        fail: (err) => {
          console.error('上传封面失败:', err);
          wx.showToast({ title: '上传失败', icon: 'none' });
        }
      });
      return;
    }

    const userId = wx.getStorageSync('userId') || '';
    const token = wx.getStorageSync('token') || '';
    const baseUrl = C.LOCAL_BASE_URL;

    wx.uploadFile({
      url: baseUrl + '/api/v1/users/cover',
      filePath,
      name: 'file',
      header: {
        'X-User-Id': userId,
        'X-Token': token,
        'X-WX-SERVICE': C.CLOUD_SERVICE_NAME
      },
      success: (res) => {
        try {
          const data = JSON.parse(res.data);
          if (data.success !== false && data.data) {
            const coverUrl = resolveAssetUrl(data.data);
            this.setData({ coverImage: coverUrl });
          } else {
            wx.showToast({ title: data.message || '上传失败', icon: 'none' });
          }
        } catch (e) {
          wx.showToast({ title: '上传失败', icon: 'none' });
        }
      },
      fail: () => {
        wx.showToast({ title: '上传失败', icon: 'none' });
      }
    });
  },

  goPointsCenter() {
    if (!this.data.pointsEnabled) return;
    wx.navigateTo({ url: '/pages/points/points' });
  },

  onShareAppMessage(e) {
    if (e && e.target && e.target.dataset && e.target.dataset.title) {
      return {
        title: e.target.dataset.title,
        imageUrl: e.target.dataset.cover || '',
        path: '/pages/trip-detail/trip-detail?id=' + e.target.dataset.id
      };
    }
    const user = this.data.userInfo;
    const stats = this.data.stats;
    let title = '拾路派 - 我的';
    if (user && user.nickName) {
      title = user.nickName + ' 的旅行主页 · 获赞 ' + (stats.totalLikes || 0);
    }
    return share.shareToFriend({ title, path: '/pages/profile/profile' });
  },

  onShareTimeline() {
    const user = this.data.userInfo;
    let title = '拾路派 - 我的';
    if (user && user.nickName) title = user.nickName + ' 的旅行主页';
    return share.shareToTimeline({ title });
  }
});
