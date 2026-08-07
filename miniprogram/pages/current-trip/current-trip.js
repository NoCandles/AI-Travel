const app = getApp();
const tmap = require('../../utils/tmap');
const api = require('../../utils/api');
const { request } = require('../../utils/request');
const { applyTheme } = require('../../utils/theme');
const mapUtils = require('../../utils/map-utils');
const { normalizeTravelMode, getTravelModeMeta } = require('../../utils/travel-mode');
const { buildMarkers } = require('../../utils/map-renderer');
const auth = require('../../utils/auth');
const { NAV_BRIDGE_HOST } = require('../../utils/config');
const share = require('../../utils/share');
const analytics = require('../../utils/analytics');
const network = require('../../utils/network');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    isLoggedIn: false,
    isLoading: true,
    offlineMode: false,
    trip: null,
    tripId: '',

    // 进度
    totalDays: 0,
    currentDay: 1,
    completedDays: 0,
    overallProgress: 0,
    totalSpotsAll: 0,
    completedSpotsAll: 0,

    // 地图
    mapData: { longitude: 118.0894, latitude: 24.4798, scale: 14 },
    markers: [],
    polylines: [],
    dayDate: '',
    routeStats: { distance: 0, duration: '', points: [] },
    routeLoaded: false,
    loadingRoute: false,
    travelMode: 'drive',
    travelModeMeta: getTravelModeMeta('drive'),
    travelModes: [
      { key: 'drive', label: '驾车', iconPath: '/images/icons/car.svg' },
      { key: 'walk', label: '步行', iconPath: '/images/icons/footprints.svg' },
      { key: 'transit', label: '公交', iconPath: '/images/icons/bus.svg' },
      { key: 'bike', label: '骑行', iconPath: '/images/icons/transport.svg' },
      { key: 'ebike', label: '电动车', iconPath: '/images/icons/transport.svg' }
    ],
    currentPathIndex: 0,
    totalPaths: 0,
    allPaths: [],
    showPathPicker: false,

    // 天气
    weather: { temp: null, desc: '', iconPath: '/images/icons/scenic.svg' },
    currentLocation: '',

    // 时间线分组
    visitedSpots: [],
    currentSpot: [],
    upcomingSpots: [],
    totalSpots: 0,
    completedSpots: 0,
    currentSpotId: '',
    currentSpotReached: false,
    currentDayHotel: null,

    // 预警
    alerts: [],

    // 弹窗
    showEndModal: false,

    _tripData: null,
    _rawPaths: null,
    _pollingTimer: null
  },

  onLoad() {
    applyTheme(this);
    // 计算导航栏高度，避开微信胶囊按钮
    this.setNavPadding();
    this.mapContext = wx.createMapContext('trip-map');
    this.loadOngoingTrip();
    this.unsubscribeNetwork = network.onNetworkChange((isOnline) => {
      if (isOnline && this.data.offlineMode) {
        this.refreshTripData();
        wx.showToast({ title: '网络已恢复，正在刷新行程', icon: 'none' });
      }
    });
    // 启用分享
    share.enableShareMenu();
  },

  /** 获取系统状态栏和胶囊按钮位置，动态设置导航栏内边距 */
  setNavPadding() {
    try {
      const sys = wx.getSystemInfoSync();
      const menu = wx.getMenuButtonBoundingClientRect();
      const statusBarHeight = sys.statusBarHeight || 44;
      // 导航栏内边距 = 胶囊按钮顶部（状态栏高度），使内容与胶囊按钮水平对齐
      const capTop = menu.top;
      const capHeight = menu.bottom - menu.top;
      this.setData({
        navTop: statusBarHeight,
        navHeight: capHeight,
        navPaddingTop: capTop,
        navPaddingRight: (sys.windowWidth - menu.left) + 4
      });
    } catch (e) {
      this.setData({ navPaddingTop: 44, navPaddingRight: 76 });
    }
  },

  onShow() {
    applyTheme(this);

    // 检查登录状态
    const isLoggedIn = auth.isLoggedIn();
    this.setData({ isLoggedIn });

    if (!isLoggedIn) {
      // 未登录：清空数据，不调接口
      this.setData({
        trip: null,
        isLoading: false,
        alerts: [],
        markers: [],
        polylines: []
      });
      return;
    }

    if (this.data.tripId) this.refreshTripData();
    // H5: 页面回到前台时重新启动天气轮询
    if (this.data.tripId && !this._pollingTimer) {
      this.startPolling();
    }
  },

  onHide() {
    // H5: 页面切入后台时停止天气轮询，避免浪费电量和流量
    if (this._pollingTimer) {
      clearInterval(this._pollingTimer);
      this._pollingTimer = null;
    }
  },

  onUnload() {
    if (this._pollingTimer) { clearInterval(this._pollingTimer); this._pollingTimer = null; }
    if (this.unsubscribeNetwork) { this.unsubscribeNetwork(); this.unsubscribeNetwork = null; }
  },

  // ==================== 数据加载 ====================

  loadOngoingTrip() {
    if (!auth.isLoggedIn()) { this.setData({ isLoading: false }); return; }
    request({ url: '/api/trips/ongoing', method: 'GET' })
      .then((trip) => {
        if (!trip) { this.setData({ isLoading: false }); return; }
        this.initTrip(trip);
      })
      .catch(() => {
        api.getTripList()
          .then((trips) => {
            const ongoing = (trips || []).find(t => t.status === 'ONGOING');
            if (!ongoing) { this.setData({ isLoading: false }); return; }
            return api.getTripDetail(ongoing.id);
          })
          .then((trip) => {
            if (trip) this.initTrip(trip);
            else this.setData({ isLoading: false });
          })
          .catch(() => {
            if (!this.loadCachedOngoingTrip()) this.setData({ isLoading: false });
          });
      });
  },

  loadCachedOngoingTrip() {
    try {
      const cache = wx.getStorageSync('ongoing_trip_offline_cache');
      const maxAge = 24 * 60 * 60 * 1000;
      if (!cache || !cache.trip || !cache.savedAt || Date.now() - cache.savedAt > maxAge) return false;
      this.initTrip(cache.trip, true);
      wx.showToast({ title: '已展示最近保存的行程', icon: 'none' });
      analytics.track('ongoing_trip_offline_cache_shown', { tripId: cache.trip.id || '' });
      return true;
    } catch (err) {
      return false;
    }
  },

  initTrip(trip, fromCache = false) {
    if (!trip || !trip.days || trip.days.length === 0) {
      this.setData({ isLoading: false });
      wx.showToast({ title: '行程数据异常', icon: 'none' });
      return;
    }
    const formatted = this.formatTripData(trip);
    const totalDays = formatted.days.length;
    let completedDays = 0;
    let totalSpotsAll = 0;
    let completedSpotsAll = 0;
    for (let i = 0; i < formatted.days.length; i++) {
      const day = formatted.days[i];
      const spots = day.points || [];
      totalSpotsAll += spots.length;
      completedSpotsAll += spots.filter(s => s.isReached).length;
      if (spots.length > 0 && spots.every(s => s.isReached)) completedDays++;
    }
    const overallProgress = totalSpotsAll > 0 ? Math.round((completedSpotsAll / totalSpotsAll) * 100) : 0;
    const snapshot = this.getNavigationSnapshot(trip.id);
    const restoredDay = snapshot && snapshot.currentDay > 0 && snapshot.currentDay <= totalDays
      ? snapshot.currentDay
      : 0;
    this._tripData = formatted;
    if (!fromCache) {
      try { wx.setStorageSync('ongoing_trip_offline_cache', { trip, savedAt: Date.now() }); } catch (err) {}
    }
    this.setData({
      trip,
      tripId: trip.id,
      isLoading: false,
      offlineMode: fromCache,
      totalDays,
      completedDays,
      overallProgress,
      totalSpotsAll,
      completedSpotsAll,
      travelMode: normalizeTravelMode(trip.travelMode || (snapshot && snapshot.travelMode) || this.data.travelMode),
      travelModeMeta: getTravelModeMeta(trip.travelMode || (snapshot && snapshot.travelMode) || this.data.travelMode)
    });
    const dayToLoad = restoredDay || (this.data.currentDay > 0 ? this.data.currentDay : 1);
    if (snapshot) {
      wx.removeStorageSync(this.getNavigationSnapshotKey(trip.id));
      analytics.track('navigation_state_restored', { tripId: trip.id, day: dayToLoad, source: snapshot.source || '' });
    }
    this.loadDay(dayToLoad);
    if (!this._pollingTimer) this.startPolling();
    this.getCurrentLocation();
  },

  formatTripData(trip) {
    const days = (trip.days || []).map(day => ({
      day: day.day,
      date: day.date ? day.date.split(' ')[0] : '',
      weather: day.weather || '',
      temperature: day.temperature || '',
      points: (day.spots || []).map((spot, idx) => ({
        id: spot.id || ('spot_' + idx),
        name: spot.name,
        arrivalTime: spot.arrivalTime || '',
        departureTime: spot.departureTime || '',
        duration: spot.duration || 60,
        weather: spot.weather || day.weather || '',
        temperature: spot.temperature || day.temperature || '',
        address: spot.address || '',
        latitude: spot.latitude || 0,
        longitude: spot.longitude || 0,
        cost: spot.cost || '',
        tips: spot.tips || '',
        isReached: spot.isReached || false,
        reachedAt: spot.reachedAt || null
      })),
      hotel: day.hotel || null
    }));
    return { name: trip.name || '', destination: trip.destination || '', days };
  },

  refreshTripData() {
    if (!this.data.tripId) return;
    api.getTripDetail(this.data.tripId).then((trip) => {
      if (!trip) return;
      if (trip.status !== 'ONGOING') {
        setTimeout(() => wx.navigateBack(), 800);
        return;
      }
      this.initTrip(trip);
    }).catch(() => {});
  },

  // ==================== 每天加载 ====================

  async loadDay(dayIndex) {
    const trip = this._tripData;
    if (!trip || !trip.days) return;
    const dayData = trip.days[dayIndex - 1];
    if (!dayData || !dayData.points || dayData.points.length === 0) {
      wx.showToast({ title: '该天无行程点', icon: 'none' });
      return;
    }

    this.setData({ loadingRoute: true, dayDate: dayData.date || '' });

    // 构建分组时间线
    const { visited, current, upcoming, total, done, currentId, currentReached } =
      this.buildGroupedTimeline(dayData);

    this.setData({
      visitedSpots: visited,
      currentSpot: current,
      upcomingSpots: upcoming,
      totalSpots: total,
      completedSpots: done,
      currentSpotId: currentId,
      currentSpotReached: currentReached,
      currentDayHotel: dayData.hotel || null,
      isDayComplete: done === total && total > 0,
      isLastDay: dayIndex === this.data.totalDays
    });

    // 加载地图（同 map.js 逻辑，精简版）
    const city = trip.destination;
    const startPoint = trip.startPoint || '';
    const pointsWithCoords = [];

    for (let i = 0; i < dayData.points.length; i++) {
      const p = dayData.points[i];
      let lat = p.latitude || 0, lng = p.longitude || 0;
      if (!lat || !lng) {
        const searchCity = (dayIndex === 1 && i === 0 && startPoint) ? startPoint : city;
        try {
          const pois = await tmap.searchPOI(p.name, searchCity);
          if (pois && pois.length > 0) { lng = pois[0].longitude; lat = pois[0].latitude; }
        } catch (e) {}
        if (!lat || !lng) {
          try {
            const geo = await tmap.geocode(p.name, searchCity);
            if (geo && geo.lat && geo.lng) { lat = geo.lat; lng = geo.lng; }
          } catch (e2) {}
        }
        if (!lat || !lng) {
          const fb = this.getFallbackCoords(i, dayData.points.length);
          lat = fb.lat; lng = fb.lng;
        }
      }
      pointsWithCoords.push({
        id: p.id || ('p' + i), name: p.name || ('点' + (i + 1)),
        arrivalTime: p.arrivalTime || '', duration: p.duration || 60,
        latitude: lat, longitude: lng,
        type: i === 0 ? 'start' : (i === dayData.points.length - 1 ? 'end' : 'spot')
      });
    }

    const centerLat = (pointsWithCoords[0] && pointsWithCoords[0].latitude) || 24.4798;
    const centerLng = (pointsWithCoords[0] && pointsWithCoords[0].longitude) || 118.0894;
    const markers = this.buildMarkers(pointsWithCoords);
    const polylines = pointsWithCoords.length >= 2 ? this.buildStraightPolyline(pointsWithCoords) : [];
    let totalDistance = 0;
    for (let i = 1; i < pointsWithCoords.length; i++) {
      totalDistance += tmap.calculateDistance(
        pointsWithCoords[i-1].longitude, pointsWithCoords[i-1].latitude,
        pointsWithCoords[i].longitude, pointsWithCoords[i].latitude
      );
    }

    this.setData({
      currentDay: dayIndex,
      mapData: { longitude: centerLng, latitude: centerLat, scale: 14 },
      markers, polylines,
      routeStats: {
        distance: (totalDistance / 1000).toFixed(1),
        duration: this.estimateDuration(pointsWithCoords),
        points: pointsWithCoords
      },
      routeLoaded: false
    });

    setTimeout(() => this.fitMapToPoints(pointsWithCoords), 300);
    if (pointsWithCoords.length >= 2) {
      this.loadRealRoute(pointsWithCoords);
    } else {
      this.setData({ loadingRoute: false });
    }
  },

  /** 将当日景点按已到达/当前/待出发分组 */
  buildGroupedTimeline(dayData) {
    const spots = dayData.points || [];
    let currentFound = false;
    const visited = [], current = [], upcoming = [];
    let total = spots.length, done = 0, currentId = '', currentReached = false;

    spots.forEach((spot, idx) => {
      const w = spot.weather || dayData.weather || '';
      const weatherIconPath = w ? (w.includes('雨') || w.includes('雪') || w.includes('雾') ? '/images/icons/hiking-warning.svg' : '/images/icons/scenic.svg') : '';
      const weatherType = w.includes('晴') ? 'sunny' : w.includes('多云') || w.includes('阴') ? 'cloudy' : w.includes('雨') ? 'rainy' : w.includes('雪') ? 'snowy' : w.includes('雾') ? 'foggy' : 'none';
      const s = {
        ...spot,
        _index: idx,
        status: '',
        weatherIconPath: weatherIconPath,
        weatherType: weatherType
      };
      // 如果景点没有独立天气，可以补充日期的天气
      if (!s.weather && dayData.weather) s.weather = dayData.weather;
      if (!s.temperature && dayData.temperature) s.temperature = dayData.temperature;
      
      if (spot.isReached) {
        s.status = 'visited';
        visited.push(s);
        done++;
      } else if (!currentFound) {
        s.status = 'current';
        current.push(s);
        currentFound = true;
        currentId = spot.id;
        currentReached = false;
      } else {
        s.status = 'todo';
        upcoming.push(s);
      }
    });

    return { visited, current, upcoming, total, done, currentId, currentReached };
  },

  // ==================== 地图渲染 ====================

  buildMarkers(points) {
    return buildMarkers(points, (type) => this.getMarkerIcon(type));
  },

  buildStraightPolyline(points) { return mapUtils.buildStraightPolyline(points); },

  loadRealRoute(points) {
    const origin = `${points[0].longitude},${points[0].latitude}`;
    const dest = `${points[points.length - 1].longitude},${points[points.length - 1].latitude}`;
    const waypoints = points.slice(1, -1).map(p => `${p.longitude},${p.latitude}`);
    const mode = this.data.travelMode;
    this.setData({ loadingRoute: true });
    tmap.getRoute(origin, dest, waypoints, mode, this._tripData ? this._tripData.destination : '')
      .then((route) => {
        if (route.paths && route.paths.length > 0) {
          this._rawPaths = route.paths;
          this.setData({
            allPaths: route.paths.map((p, i) => ({
              index: i, distance: p.distance, duration: p.duration,
              label: this.getPathLabel(i, p, route.paths.length),
              distanceText: (p.distance / 1000).toFixed(1) + 'km'
            })),
            totalPaths: route.paths.length, currentPathIndex: 0
          });
          this.renderPath(route.paths[0], mode, points);
        } else { this.setData({ loadingRoute: false }); }
      })
      .catch(() => { this.setData({ loadingRoute: false }); });
  },

  renderPath(path, mode, points) {
    let coordPoints;
    if (path.polyline_decoded && path.polyline && path.polyline.length > 0) {
      coordPoints = this.convertDecodedPolyline(path.polyline);
    } else {
      coordPoints = this.decodePolyline(path.polyline);
    }
    if (coordPoints.length === 0) { this.setData({ loadingRoute: false }); return; }
    const style = this.getPolylineStyle(mode);
    let polylines;
    if (path.steps && path.steps.length > 0 && this.hasPolylineIdx(path.steps)) {
      polylines = this.buildSegmentedPolylines(coordPoints, path.steps, style, mode);
    } else {
      polylines = [{
        points: coordPoints, color: style.color, width: style.width,
        arrowLine: style.arrowLine || false, dottedLine: style.dottedLine || false,
        borderColor: style.borderColor || '', borderWidth: style.borderWidth || 0
      }];
    }
    const baseMarkers = (this.data.markers || []).filter(m => m.id < 1000);
    this.setData({
      markers: baseMarkers, polylines, routeLoaded: true, loadingRoute: false,
      'routeStats.distance': (path.distance / 1000).toFixed(1),
      'routeStats.duration': this.formatDuration(path.duration)
    });
    setTimeout(() => this.fitMapToPoints(this.data.routeStats.points), 100);
  },

  togglePathPicker() { this.setData({ showPathPicker: !this.data.showPathPicker }); },

  switchPath(e) {
    const index = parseInt(e.detail.index);
    if (index === this.data.currentPathIndex) return;
    const points = this.data.routeStats.points;
    const mode = this.data.travelMode;
    if (this._rawPaths && this._rawPaths.length > index) {
      this.setData({ currentPathIndex: index, showPathPicker: false });
      this.renderPath(this._rawPaths[index], mode, points);
      return;
    }
    this.setData({ currentPathIndex: index, showPathPicker: false, loadingRoute: true });
    if (points && points.length >= 2) {
      const origin = `${points[0].longitude},${points[0].latitude}`;
      const dest = `${points[points.length - 1].longitude},${points[points.length - 1].latitude}`;
      const wp = points.slice(1, -1).map(p => `${p.longitude},${p.latitude}`);
      tmap.getRoute(origin, dest, wp, mode, this._tripData ? this._tripData.destination : '')
        .then((route) => {
          if (route.paths && route.paths.length > index) {
            this._rawPaths = route.paths;
            this.renderPath(route.paths[index], mode, points);
          } else { this.setData({ loadingRoute: false }); }
        }).catch(() => this.setData({ loadingRoute: false }));
    }
  },

  // ==================== 打卡 ====================

  checkInCurrentSpot() {
    const spotId = this.data.currentSpotId;
    if (!spotId) { wx.showToast({ title: '没有待打卡的景点', icon: 'none' }); return; }
    if (this.data.currentSpotReached) { wx.showToast({ title: '已打卡过了', icon: 'none' }); return; }
    request({ url: `/api/trips/spots/${spotId}/check-in`, method: 'POST' })
      .then(() => {
        this.refreshTripData();
      })
      .catch((err) => { wx.showToast({ title: (err && err.message) || '打卡失败', icon: 'none' }); });
  },

  switchDay(e) {
    const day = parseInt(e.currentTarget.dataset.day);
    if (day !== this.data.currentDay) this.loadDay(day);
  },

  /** 今日全部打卡完成 → 切换到下一天或结束行程 */
  goToNextDay() {
    const { currentDay, totalDays, isLastDay } = this.data;
    if (isLastDay) {
      // 最后一天，触发结束
      this.setData({ showEndModal: true });
    } else {
      // 切换到下一天
      const nextDay = currentDay + 1;
      if (nextDay <= totalDays) {
        this.loadDay(nextDay);
      }
    }
  },

  onTimelineTap() {},  // 保留扩展点

  // ==================== 导航 ====================

  getNavigationSnapshotKey(tripId) {
    return `current_trip_navigation_snapshot_${tripId || this.data.tripId || ''}`;
  },

  saveNavigationSnapshot(source) {
    const tripId = this.data.tripId;
    if (!tripId) return;
    const snapshot = {
      tripId,
      currentDay: this.data.currentDay,
      currentSpotId: this.data.currentSpotId,
      travelMode: this.data.travelMode,
      source: source || 'navigation',
      savedAt: Date.now()
    };
    try {
      wx.setStorageSync(this.getNavigationSnapshotKey(tripId), snapshot);
      analytics.track('navigation_state_saved', { tripId, day: snapshot.currentDay, source: snapshot.source });
    } catch (err) {
      console.warn('[current-trip] save navigation snapshot failed', err);
    }
  },

  getNavigationSnapshot(tripId) {
    try {
      const snapshot = wx.getStorageSync(this.getNavigationSnapshotKey(tripId));
      if (!snapshot || snapshot.tripId !== tripId) return null;
      if (Date.now() - snapshot.savedAt > 24 * 60 * 60 * 1000) return null;
      return snapshot;
    } catch (err) {
      return null;
    }
  },

  goToNavigation() {
    const points = this.data.routeStats.points;
    if (!points || points.length < 2) { wx.showToast({ title: '至少需要 2 个途经点', icon: 'none' }); return; }
    wx.showActionSheet({
      itemList: ['高德地图（推荐）', '腾讯地图'],
      success: (res) => {
        if (res.tapIndex === 0) this.openAmapNavigation(points);
        else this.openTencentNavigation(points);
      }
    });
  },

  openAmapNavigation(points) {
    this.saveNavigationSnapshot('amap');
    mapUtils.openAmapNavigation(points, this.data.travelMode, NAV_BRIDGE_HOST);
  },

  openTencentNavigation(points) {
    this.saveNavigationSnapshot('tencent_map');
    mapUtils.openTencentNavigation(points, this.data.travelMode, app.globalData.tmapKey);
  },

  // ==================== 结束行程 ====================

  endTrip() { this.setData({ showEndModal: true }); },
  hideEndModal() { this.setData({ showEndModal: false }); },

  confirmEndTrip() {
    this.setData({ showEndModal: false });
    request({ url: `/api/trips/${this.data.tripId}/complete`, method: 'POST' })
      .then(() => {
        setTimeout(() => wx.navigateBack(), 800);
      })
      .catch((err) => { wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' }); });
  },

  // ==================== 预警 ====================

  startPolling() {
    this._pollingTimer = setInterval(() => this.fetchWeatherForAlerts(), 300000);
    this.fetchWeatherForAlerts();
  },

  fetchWeatherForAlerts() {
    const dest = this._tripData ? this._tripData.destination : '';
    if (!dest) return;
    tmap.geocode(dest).then((geo) => {
      if (geo && geo.lat && geo.lng) {
        return request({ url: `/api/map/weather?latitude=${geo.lat}&longitude=${geo.lng}`, method: 'GET' });
      }
      return null;
    }).then((data) => {
      if (data) { this.applyWeatherData(data); this.generateAlerts(data); }
    }).catch(() => {});
  },

  applyWeatherData(data) {
    let now = {};
    if (data.realtime && Array.isArray(data.realtime) && data.realtime.length > 0) {
      now = data.realtime[0].infos || data.realtime[0] || {};
    } else if (data.obs) { now = data.obs; }
    const temp = now.temperature || now.temp || '';
    const desc = now.weather || '';
    let iconPath = '/images/icons/scenic.svg';
    if (desc.includes('雨') || desc.includes('雪') || desc.includes('雾')) iconPath = '/images/icons/hiking-warning.svg';
    this.setData({ 'weather.temp': temp, 'weather.desc': desc, 'weather.iconPath': iconPath });
  },

  generateAlerts() {
    const alerts = [];
    const todaySpots = this.data.visitedSpots.concat(this.data.currentSpot).concat(this.data.upcomingSpots);
    const { desc, temp } = this.data.weather;
    if (desc) {
      if (desc.includes('雨') || desc.includes('暴雨')) {
        alerts.push({ iconPath: '/images/icons/hiking-warning.svg', title: desc.includes('暴') ? `${desc}，注意安全` : `${desc}，记得带伞`, severity: desc.includes('暴') ? 'danger' : 'warning' });
      }
      if (desc.includes('雪') || desc.includes('暴雪')) {
        alerts.push({ iconPath: '/images/icons/hiking-warning.svg', title: '降雪天气，路面湿滑', severity: 'danger' });
      }
      if (desc.includes('雾')) {
        alerts.push({ iconPath: '/images/icons/hiking-warning.svg', title: '大雾，能见度低，谨慎驾驶', severity: 'warning' });
      }
      if (desc.includes('晴') && temp && parseInt(temp) > 35) {
        alerts.push({ iconPath: '/images/icons/scenic.svg', title: `高温 ${temp}°C，注意防暑`, severity: 'warning' });
      }
    }
    const nextSpot = todaySpots.find(s => s.status === 'current');
    if (nextSpot) {
      alerts.push({ iconPath: '/images/icons/location-pin.svg', title: `下一站：${nextSpot.name}`, severity: 'info' });
    }
    if (alerts.length === 0) {
      alerts.push({ iconPath: '/images/icons/check.svg', title: '当前一切顺利，旅途愉快！', severity: 'info' });
    }
    const severityWeight = { danger: 3, warning: 2, info: 1 };
    const seenTitles = {};
    const deduped = alerts
      .filter((item) => {
        if (seenTitles[item.title]) return false;
        seenTitles[item.title] = true;
        return true;
      })
      .sort((a, b) => severityWeight[b.severity] - severityWeight[a.severity]);

    // 高风险最多一条、中风险最多一条，再保留一条行程信息，避免同屏告警淹没重点。
    const visible = [];
    const countBySeverity = { danger: 0, warning: 0, info: 0 };
    deduped.forEach((item) => {
      const limit = item.severity === 'danger' ? 1 : (item.severity === 'warning' ? 1 : 1);
      if (countBySeverity[item.severity] < limit && visible.length < 3) {
        visible.push(item);
        countBySeverity[item.severity] += 1;
      }
    });
    this.setData({ alerts: visible });
    analytics.track('risk_alerts_rendered', {
      total: alerts.length,
      visible: visible.length,
      danger: countBySeverity.danger,
      warning: countBySeverity.warning
    });
  },

  // ==================== 定位 ====================

  getCurrentLocation() {
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        tmap.reverseGeocode(res.longitude, res.latitude)
          .then((result) => {
            const city = (result.address_component && result.address_component.city) || '';
            const district = (result.address_component && result.address_component.district) || '';
            this.setData({ currentLocation: city ? (city + (district ? ' · ' + district : '')) : '当前位置' });
          }).catch(() => { this.setData({ currentLocation: this._tripData ? this._tripData.destination : '' }); });
      },
      fail: () => { this.setData({ currentLocation: this._tripData ? this._tripData.destination : '' }); }
    });
  },

  // ==================== 跳转 ====================

  goBack() { wx.navigateBack(); },
  goToMyTrips() { wx.switchTab({ url: '/pages/my-trips/my-trips' }); },
  goToTripDetail() { if (this.data.tripId) wx.navigateTo({ url: '/pages/trip-detail/trip-detail?id=' + this.data.tripId }); },

  /** 点击地址，打开手机地图导航 */
  async openLocation(e) {
    const dataset = e.currentTarget.dataset;
    const lat = parseFloat(dataset.lat);
    const lng = parseFloat(dataset.lng);
    const name = dataset.name || '目的地';
    const address = dataset.address || '';
    const city = this._tripData ? this._tripData.destination : '';

    // 有坐标直接用
    if (lat && lng) {
      this.saveNavigationSnapshot('location');
      wx.openLocation({ latitude: lat, longitude: lng, name: name, scale: 16 });
      return;
    }

    // 无坐标，用景点名称 POI 搜索
    try {
      const pois = await tmap.searchPOI(name, city);
      if (pois && pois.length > 0 && pois[0].latitude) {
        this.saveNavigationSnapshot('location');
        wx.openLocation({
          latitude: pois[0].latitude,
          longitude: pois[0].longitude,
          name: pois[0].title || name,
          scale: 16
        });
        return;
      }
      // POI 无结果，降级为地址编码
      const result = await tmap.geocode(address || name, city);
      if (result && result.lat && result.lng) {
        this.saveNavigationSnapshot('location');
        wx.openLocation({ latitude: result.lat, longitude: result.lng, name: name, scale: 16 });
      } else {
        wx.showToast({ title: '无法定位该地点', icon: 'none' });
      }
    } catch (err) {
      wx.showToast({ title: '定位失败，请稍后重试', icon: 'none' });
    }
  },

  // ==================== 工具方法 ====================

  onMarkerTap(e) {
    const point = this.data.routeStats.points[(e.detail.markerId || 1) - 1];
    if (point) wx.showToast({ title: point.name + (point.arrivalTime ? ' ' + point.arrivalTime : ''), icon: 'none', duration: 1500 });
  },
  fitMapToPoints(points) {
    mapUtils.fitMapToPoints('trip-map', points);
  },
  getMarkerIcon(type) { return mapUtils.getMarkerIcon(type); },
  getFallbackCoords(i, t) { return mapUtils.getFallbackCoords(i, t); },
  estimateDuration(p) { return mapUtils.estimateDuration(p); },
  formatDuration(m) { return mapUtils.formatDuration(m); },
  getPathLabel(i, p, t) { return mapUtils.getPathLabel(i, p, t); },
  getPolylineStyle(m) { return mapUtils.getPolylineStyle(m); },
  hasPolylineIdx(s) { return mapUtils.hasPolylineIdx(s); },
  buildSegmentedPolylines(c, s, b, m) { return mapUtils.buildSegmentedPolylines(c, s, b, m); },
  convertDecodedPolyline(d) { return mapUtils.convertDecodedPolyline(d); },
  decodePolyline(e) { return mapUtils.decodePolyline(e); },
  noop() {},

  // ==================== 分享 ====================

  onShareAppMessage() {
    const trip = this.data.trip;
    const tripData = this._tripData;
    if (trip && trip.id) {
      return {
        title: '我正在' + (trip.destination || '旅途中') + '旅行！' + (trip.name || ''),
        imageUrl: trip.coverImage || '',
        path: '/pages/trip-detail/trip-detail?publishId=' + trip.id
      };
    }
    return share.shareToFriend({
      title: '拾路派 - 旅途进行中',
      path: '/pages/current-trip/current-trip'
    });
  },

  onShareTimeline() {
    const trip = this.data.trip;
    if (trip && trip.id) {
      return {
        title: '我正在' + (trip.destination || '旅途中') + '旅行！',
        imageUrl: trip.coverImage || '',
        query: 'publishId=' + trip.id
      };
    }
    return share.shareToTimeline({
      title: '拾路派 - 旅途进行中'
    });
  }
});
