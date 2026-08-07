const app = getApp();
const api = require('../../utils/api');
const auth = require('../../utils/auth');
const cache = require('../../utils/cache');
const { handleNotLogin } = require('../../utils/request');
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');
const { getTravelModeMeta, normalizeTravelMode } = require('../../utils/travel-mode');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    trips: [],
    filteredTrips: [],
    currentTab: 0,
    stats: { all: 0, ongoing: 0, completed: 0, favorite: 0 },
    shareTripId: null,
    page: 1,
    hasMore: true,
    loadingMore: false
  },

  _hasLoaded: false,

  onLoad() {
    applyTheme(this);
    // 启用分享
    share.enableShareMenu();
  },

  onShow() {
    applyTheme(this);
    if (!auth.isLoggedIn()) {
      this.setData({ trips: [], stats: { all: 0, ongoing: 0, completed: 0, favorite: 0 } });
      this._hasLoaded = false;
      return;
    }

    // 已有缓存数据且未被标记为脏数据时，跳过接口请求
    if (this._hasLoaded && !app.globalData.tripListDirty && this.data.trips.length > 0) {
      return;
    }

    this.setData({ page: 1, hasMore: true });

    // 缓存优先：先展示上次的行程列表，再静默刷新
    const cached = cache.get('my_trips_p1');
    if (cached && !app.globalData.tripListDirty) {
      this.setData({
        trips: cached.trips || [],
        filteredTrips: cached.filteredTrips || cached.trips || [],
        stats: cached.stats || { all: 0, ongoing: 0, completed: 0, favorite: 0 }
      });
    }

    this.loadTripsAndStats();
  },

  /** 根据 ID 生成稳定的占位封面 URL */
  _getPlaceholderCover(id, seed) {
    if (!id && !seed) return '/images/default-cover.png';
    const picId = (Math.abs(parseInt(String(id).replace(/\D/g, '')) || (seed || 0)) % 85) + 10;
    return 'https://picsum.photos/id/10' + picId + '/600/800';
  },

  loadTripsAndStats() {
    const { page } = this.data;
    app.globalData.tripListDirty = false;
    api.getTripList({ page, size: 20 })
      .then((trips) => {
        const list = trips || [];
        const hasMore = list.length === 20;
        const formattedTrips = list.map((t, idx) => {
          const rawTravelMode = t.travelMode || 'drive';
          const travelMode = normalizeTravelMode(rawTravelMode);
          const travelModeMeta = getTravelModeMeta(rawTravelMode);
          return {
            id: t.id,
            title: t.name || (t.destination + '之旅'),
            destination: t.destination,
            days: t.days ? t.days.length : (t.daysCount || t.days || 0),
            date: t.startDate ? t.startDate.split(' ')[0] : '',
            status: t.status,
            statusText: t.status === 'ONGOING' ? '进行中' : (t.status === 'COMPLETED' ? '已完成' : '待出发'),
            tags: t.tags && t.tags.length ? t.tags : (travelMode === 'walk' ? ['徒步', 'AI 路线'] : []),
            coverImage: t.coverImage || this._getPlaceholderCover(t.id, idx + 100),
            travelMode,
            travelModeMeta,
            isHiking: travelMode === 'walk'
          };
        });
        const hikingTrips = page === 1 ? this._getLocalHikingTrips() : [];
        const allTrips = page === 1 ? [...hikingTrips, ...formattedTrips] : [...this.data.trips, ...formattedTrips];
        const ongoing = allTrips.filter(t => t.status === 'ONGOING').length;
        const completed = allTrips.filter(t => t.status === 'COMPLETED').length;
        const all = allTrips.length;
        this.setData({
          trips: allTrips,
          filteredTrips: allTrips,
          stats: { all, ongoing, completed, favorite: 0 },
          hasMore,
          loadingMore: false
        });
        // 缓存第一页行程列表
        if (page === 1) {
          cache.set('my_trips_p1', {
            trips: allTrips,
            filteredTrips: allTrips,
            stats: { all, ongoing, completed, favorite: 0 }
          });
          this._hasLoaded = true;
        }
      })
      .catch((err) => {
        console.error('加载行程失败:', err);
        // 只有确实没有数据时才清空（保留缓存兜底）
        if (!this.data.trips || this.data.trips.length === 0) {
          this.setData({ trips: [], filteredTrips: [], stats: { all: 0, ongoing: 0, completed: 0, favorite: 0 } });
        }
        this.setData({ loadingMore: false });
        // 统一登录拦截
        handleNotLogin(err);
      });
  },

  onPullDownRefresh() {
    this.setData({ page: 1, hasMore: true });
    this.loadTripsAndStats();
    wx.stopPullDownRefresh();
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loadingMore) return;
    this.setData({ loadingMore: true, page: this.data.page + 1 });
    this.loadTripsAndStats();
  },

  createNewTrip() {
    wx.navigateTo({ url: '/pages/chat/chat' });
  },

  viewTripDetail(e) {
    const id = e.currentTarget.dataset.id;
    const trip = this.data.trips.find(item => item.id === id);
    if (trip && trip.isHiking) {
      wx.navigateTo({ url: '/pages/hiking-route/hiking-route?tripId=' + encodeURIComponent(id) });
      return;
    }
    const hikingPlan = (wx.getStorageSync('myHikingPlans') || []).find(item => item.id === id);
    if (hikingPlan) {
      wx.setStorageSync('activeHikingPlan', hikingPlan);
      wx.navigateTo({ url: '/pages/hiking-route/hiking-route?planId=' + id });
      return;
    }
    wx.navigateTo({ url: '/pages/planner/planner?id=' + id });
  },

  _getLocalHikingTrips() {
    return (wx.getStorageSync('myHikingPlans') || []).filter(plan => !plan.tripId).map((plan, index) => ({
      id: plan.id,
      title: `${plan.destination}徒步`,
      destination: plan.destination,
      days: 1,
      date: plan.startDate || '',
      status: plan.status || 'PLANNED',
      statusText: plan.status === 'ONGOING' ? '进行中' : (plan.status === 'COMPLETED' ? '已完成' : '待出发'),
      tags: ['徒步', plan.difficulty || '轻中度'],
      coverImage: this._getPlaceholderCover(plan.id, index),
      travelMode: 'walk',
      travelModeMeta: getTravelModeMeta('walk'),
      isHiking: true
    }));
  },

  editTrip(e) {
    const id = e.currentTarget.dataset.id;
    const trip = this.data.trips.find(item => item.id === id);
    if (trip && trip.isHiking) {
      this.viewTripDetail(e);
      return;
    }
    wx.navigateTo({ url: '/pages/planner/planner?id=' + id });
  },

  /** 开始行程 */
  startTrip(e) {
    const id = e.currentTarget.dataset.id;
    const trip = this.data.trips.find(item => item.id === id);
    if (trip && trip.isHiking) {
      wx.navigateTo({ url: '/pages/hiking-route/hiking-route?tripId=' + encodeURIComponent(id) });
      return;
    }
    wx.showModal({
      title: '确认出发',
      content: '开始行程后将开始计算轨迹，确定要出发吗？',
      success: (res) => {
        if (!res.confirm) return;
        api.startTrip(id)
          .then(() => {
            this.loadTripsAndStats();
          })
          .catch((err) => {
            wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' });
            handleNotLogin(err);
          });
      }
    });
  },

  /** 结束行程 */
  completeTrip(e) {
    const id = e.currentTarget.dataset.id;
    const trip = this.data.trips.find(item => item.id === id);
    if (trip && trip.isHiking) {
      wx.navigateTo({ url: '/pages/hiking-route/hiking-route?tripId=' + encodeURIComponent(id) });
      return;
    }
    wx.showModal({
      title: '确认结束',
      content: '结束行程后该行程将计入你的旅行足迹，确定结束吗？',
      success: (res) => {
        if (!res.confirm) return;
        api.completeTrip(id)
          .then(() => {
            this.loadTripsAndStats();
          })
          .catch((err) => {
            wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' });
            handleNotLogin(err);
          });
      }
    });
  },

  shareTrip(e) {
    const id = e.currentTarget.dataset.id;
    this.setData({ shareTripId: id });
  },

  onShareAppMessage() {
    const trip = this.data.trips.find(t => t.id === this.data.shareTripId);
    if (trip) {
      return {
        title: trip.title || trip.destination + '之旅',
        imageUrl: trip.image || trip.coverImage || '',
        path: '/pages/trip-detail/trip-detail?publishId=' + trip.id
      };
    }
    return share.shareToFriend({
      title: '拾路派 - 我的旅程',
      path: '/pages/my-trips/my-trips'
    });
  },

  onShareTimeline() {
    const trip = this.data.trips.find(t => t.id === this.data.shareTripId);
    if (trip) {
      return {
        title: trip.title || trip.destination + '之旅',
        imageUrl: trip.image || trip.coverImage || '',
        query: 'publishId=' + trip.id
      };
    }
    return share.shareToTimeline({
      title: '拾路派 - 我的旅程'
    });
  },

  deleteTrip(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这个行程吗？',
      success: (res) => {
        if (res.confirm) {
          api.deleteTrip(id)
            .then(() => {
              app.globalData.tripListDirty = true;
              app.globalData.squareDirty = true;
              app.globalData.statsDirty = true;
              this.loadTripsAndStats();
            })
            .catch((err) => {
              console.error('删除失败:', err);
            });
        }
      }
    });
  },

  // 切换行程分类Tab
  switchTab(e) {
    const tab = parseInt(e.currentTarget.dataset.tab);
    this.setData({ currentTab: tab });
    this.filterTrips();
  },

  // 过滤行程
  filterTrips() {
    const { trips, currentTab } = this.data;
    let filtered = trips;

    if (currentTab === 1) {
      // 进行中
      filtered = trips.filter(t => t.status === 'ONGOING');
    } else if (currentTab === 2) {
      // 已完成
      filtered = trips.filter(t => t.status === 'COMPLETED');
    }

    this.setData({ filteredTrips: filtered });
  },

  // 跳转到AI生成页面
  goToAIGenerate() {
    wx.navigateTo({ url: '/pages/chat/chat' });
  },

  /** 跳转到当前行程页面 */
  goToCurrentTrip() {
    api.getOngoingTrip().then((trip) => {
      if (trip && trip.travelMode === 'hiking') {
        wx.navigateTo({ url: '/pages/hiking-route/hiking-route?tripId=' + encodeURIComponent(trip.id) });
        return;
      }
      wx.navigateTo({ url: '/pages/current-trip/current-trip' });
    }).catch(() => {
      wx.navigateTo({ url: '/pages/current-trip/current-trip' });
    });
  },

  onPullDownRefresh() {
    if (auth.isLoggedIn()) {
      this.loadTripsAndStats();
    }
    wx.stopPullDownRefresh();
  }
});
