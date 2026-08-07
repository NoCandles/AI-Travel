const app = getApp();
const api = require('../../utils/api');
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');
const auth = require('../../utils/auth');
const { generateVisitedPolygons } = require('../../utils/cityBoundary');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    loading: true,
    activeTab: 'map',
    // 足迹数据
    cities: [],
    totalTrips: 0,
    totalCities: 0,
    // 地图 — 默认展示全中国
    mapLatitude: 35.5,
    mapLongitude: 104.5,
    mapScale: 4,
    polygons: [],
    // 全量城市（含未到过的）
    allCities: []
  },

  onLoad() {
    if (!auth.checkLogin()) return;
    applyTheme(this);
    this.loadFootprint();
    share.enableShareMenu();
  },

  onShow() {
    applyTheme(this);
  },

  loadFootprint() {
    this.setData({ loading: true });
    Promise.all([
      api.getFootprintDetail(),
      api.getCities()
    ]).then(([footprintList, allCitiesList]) => {
      const visitedMap = {};
      const cities = (footprintList || []).map(item => {
        const name = item.destination || item;
        const tripCount = item.tripCount || 1;
        visitedMap[name] = tripCount;
        return { name, tripCount };
      });

      let total = 0;
      cities.forEach(c => total += c.tripCount);

      // 为所有城市标注是否到过
      const allCities = (allCitiesList || []).map(c => ({
        ...c,
        visited: !!visitedMap[c.name],
        tripCount: visitedMap[c.name] || 0
      }));

      this.setData({
        cities,
        allCities,
        totalTrips: total,
        totalCities: cities.length,
        loading: false
      });

      this._generateMarkers();
    }).catch((err) => {
      console.error('加载足迹失败:', err);
      this.setData({ cities: [], loading: false, totalTrips: 0, totalCities: 0 });
    });
  },

  _generateMarkers() {
    const { allCities } = this.data;

    // 到访城市：生成简化边界多边形
    const visitedCities = allCities.filter(c => c.visited && c.lng && c.lat);
    const polygons = generateVisitedPolygons(visitedCities);

    // 缓存到访城市映射，供 polygon 点击使用
    const visitedMap = {};
    visitedCities.forEach(c => {
      visitedMap[c.id] = { name: c.name, tripCount: c.tripCount };
    });
    this._visitedMap = visitedMap;

    this.setData({ polygons });
  },

  // 点击多边形显示城市信息
  onPolygonTap(e) {
    const id = e.detail.polygonId;
    const info = this._visitedMap && this._visitedMap[id];
    if (info) {
      wx.showToast({ title: info.name + ' · ' + info.tripCount + '次', icon: 'none', duration: 2000 });
    }
  },

  switchTab(e) {
    this.setData({ activeTab: e.currentTarget.dataset.tab });
  },

  goBack() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile' }) });
  },

  onShareAppMessage() {
    const { totalCities, totalTrips } = this.data;
    let title = '拾路派 - 我的旅行足迹';
    if (totalCities > 0) {
      title = '我去过' + totalCities + '个城市' + totalTrips + '段旅程 - 拾路派足迹';
    }
    return share.shareToFriend({ title, path: '/pages/footprint/footprint' });
  },

  onShareTimeline() {
    const { totalCities } = this.data;
    let title = '拾路派 - 我的旅行足迹';
    if (totalCities > 0) title = '我去过' + totalCities + '个城市 - 拾路派';
    return share.shareToTimeline({ title });
  }
});
