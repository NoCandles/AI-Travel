const app = getApp();
const tmap = require('../../utils/tmap');
const api = require('../../utils/api');
const { request } = require('../../utils/request');
const { applyTheme } = require('../../utils/theme');
const mapUtils = require('../../utils/map-utils');
const { normalizeTravelMode, getTravelModeMeta } = require('../../utils/travel-mode');
const { NAV_BRIDGE_HOST } = require('../../utils/config'); // M3: 统一配置
const share = require('../../utils/share');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    mapData: {
      longitude: 118.0894,
      latitude: 24.4798,
      scale: 14
    },
    // 途经点标记 + polyline 路线连线
    markers: [],
    polylines: [],
    currentDay: 1,
    totalDays: 1,
    routeStats: {
      distance: 0,
      duration: '',
      points: []
    },
    destination: '',
    tripId: '',
    routeId: 'seasonal',
    startPoint: '',
    loaded: false,
    routeLoaded: false,
    editMode: false,
    travelMode: 'drive',
    travelModeMeta: getTravelModeMeta('drive'),
    travelModes: [
      { key: 'drive', label: '驾车', iconPath: '/images/icons/car.svg' },
      { key: 'walk', label: '步行', iconPath: '/images/icons/footprints.svg' },
      { key: 'transit', label: '公交', iconPath: '/images/icons/bus.svg' },
      { key: 'bike', label: '骑行', iconPath: '/images/icons/transport.svg' },
      { key: 'ebike', label: '电动车', iconPath: '/images/icons/transport.svg' }
    ],
    // 多路线支持
    currentPathIndex: 0,
    totalPaths: 0,
    allPaths: [],
    showPathPicker: false,
    showAddModal: false,
    addPointName: '',
    editingIndex: -1,
    showEditModal: false,
    editPointName: '',
    editPointTime: '',
    editPointDuration: 60,
    loadingRoute: false,
    currentLocation: '',
    panelCollapsed: false,
    panelDragging: false,
    panelStartY: 0,
    panelTranslate: 0,
    weather: {
      temp: null,
      desc: '',
      iconPath: '/images/icons/scenic.svg'
    },
    dayDate: ''
  },

  onLoad(options) {
    applyTheme(this);
    const destination = decodeURIComponent(options.destination || '');
    const tripId = options.tripId || '';
    const routeId = options.routeId || 'seasonal';
    const startPoint = decodeURIComponent(options.startPoint || '');
    const travelMode = normalizeTravelMode(options.travelMode);
    this.setData({ destination, tripId, routeId, startPoint, travelMode, travelModeMeta: getTravelModeMeta(travelMode) });
    this.mapContext = wx.createMapContext('map');
    this.getCurrentLocation();
    this.loadFromTripData();
    // 启用分享
    share.enableShareMenu();
  },

  onReady() {
    // 页面渲染完成
  },

  onShow() {
    applyTheme(this);
  },

  loadFromTripData() {
    const tripId = this.data.tripId;

    if (tripId) {
      api.getTripDetail(tripId)
        .then((trip) => {
          if (!trip || !trip.days || trip.days.length === 0) {
            wx.showToast({ title: '未找到行程数据', icon: 'none' });
            return;
          }
          const formattedTrip = this.formatTripData(trip);
          const travelMode = normalizeTravelMode(trip.travelMode);
          this.setData({ totalDays: formattedTrip.days.length, travelMode, travelModeMeta: getTravelModeMeta(travelMode) });
          if (!this.data.destination && trip.destination) {
            this.setData({ destination: trip.destination });
          }
          this._tripData = formattedTrip;
          this.loadDay(1);
        })
        .catch((err) => {
          console.error('加载行程失败:', err);
          this.loadFromGlobalData();
        });
      return;
    }

    this.loadFromGlobalData();
  },

  /**
   * 验证 destination 是否为有效城市名。
   * 路线名(如"格聂南线")、景区名等不是城市，不能用于 geocode/searchPOI 的 city 参数。
   * 优先从景点 address 中提取真实城市，否则返回空串做全国搜索。
   */
  resolveCity(dayData) {
    const dest = (this.data.destination || '').trim();
    if (!dest) return '';

    // 有效行政区后缀 —— 以这些结尾的基本是真实地名
    const validSuffixes = ['省', '市', '县', '区', '州', '盟', '自治区', '自治州'];
    if (validSuffixes.some(s => dest.endsWith(s))) return dest;

    // 在 constants.CITIES 中匹配
    const cities = (require('../../config/constants').CITIES || []);
    for (const c of cities) {
      if (c.name === dest || (c.keys && c.keys.includes(dest))) {
        return c.name;
      }
    }

    // 尝试从当天景点 address 中提取城市（address 通常是 "四川省甘孜州理塘县xxx" 格式）
    if (dayData && dayData.points) {
      const addrPatterns = [
        /([\u4e00-\u9fa5]{2,4}(?:省|自治区))/,
        /([\u4e00-\u9fa5]{2,4}(?:市|州|盟))/,
        /([\u4e00-\u9fa5]{2,4}(?:县|区|旗))/
      ];
      for (const p of dayData.points) {
        const addr = p.address || '';
        if (!addr) continue;
        // 优先取市级
        for (const pat of addrPatterns) {
          const m = addr.match(pat);
          if (m && m[1]) {
            const extracted = m[1];
            // 避免提取到与 destination 相同的无效名
            if (extracted !== dest) {
              console.log('[resolveCity] 从景点地址提取城市:', extracted, '原始destination:', dest);
              return extracted;
            }
          }
        }
      }
    }

    console.warn('[resolveCity] destination不是有效城市名，降级为全国搜索:', dest);
    return '';
  },

  /**
   * 缓存解析出的坐标：更新内存 _tripData + 批量保存到后端 DB
   * 下次打开同一行程时 p.latitude/p.longitude 非零，直接跳过 API 调用
   */
  _cacheSpotCoords(originalPoints, pointsWithCoords) {
    const toSave = [];

    for (let i = 0; i < originalPoints.length; i++) {
      const orig = originalPoints[i];
      const resolved = pointsWithCoords[i];
      if (!resolved || !resolved.latitude || !resolved.longitude) continue;

      // 只缓存之前没有坐标的景点（已有坐标的不覆盖）
      if (!orig.latitude || !orig.longitude) {
        orig.latitude = resolved.latitude;
        orig.longitude = resolved.longitude;

        // 收集需要保存到后端的（需要有真实 spotId，排除临时 id）
        if (orig.id && !String(orig.id).startsWith('spot_') && !String(orig.id).startsWith('p_')) {
          toSave.push({
            spotId: orig.id,
            latitude: resolved.latitude,
            longitude: resolved.longitude
          });
        }
      }
    }

    // 批量保存到后端
    if (toSave.length > 0) {
      request({
        url: '/api/trips/spots/coords',
        method: 'PUT',
        data: toSave,
        silent: true
      }).then(() => {
        console.log('[coords缓存] 已保存', toSave.length, '个景点坐标');
      }).catch((e) => {
        console.warn('[coords缓存] 保存失败:', e);
      });
    }
  },

  formatTripData(trip) {
    const routeId = this.data.routeId || 'seasonal';
    const selectedRoute = this.pickRoute(trip, routeId);
    const sourceDays = selectedRoute && selectedRoute.days && selectedRoute.days.length
      ? selectedRoute.days
      : (trip.days || []);
    const days = sourceDays.map(day => ({
      day: day.day,
      date: day.date ? day.date.split(' ')[0] : '',
      weather: day.weather || '',
      temperature: day.temperature || '',
      points: ((day.points || day.spots) || []).map((spot, idx) => ({
        id: spot.id || ('spot_' + idx),
        name: spot.name,
        arrivalTime: spot.arrivalTime || '',
        duration: spot.duration || 60,
        weather: spot.weather || day.weather || '',
        temperature: spot.temperature || day.temperature || '',
        address: spot.address || '',
        latitude: spot.latitude || 0,
        longitude: spot.longitude || 0,
        cost: spot.cost || '',
        tips: spot.tips || ''
      })),
      hotel: day.hotel || null
    }));

    return {
      name: trip.name || '',
      destination: trip.destination || '',
      days
    };
  },

  pickRoute(trip, routeId) {
    const routes = trip && trip.routes;
    if (!routes || !routes.length) return null;
    return routes.find(r => r.id === routeId) || routes[0];
  },

  loadFromGlobalData() {
    const trip = app.globalData.currentTrip;
    if (!trip || !trip.days || trip.days.length === 0) {
      wx.showToast({ title: '未找到行程数据，请先生成行程', icon: 'none' });
      return;
    }

    const formattedTrip = this.formatTripData(trip);
    const totalDays = formattedTrip.days.length;
    let destination = this.data.destination;
    if (!destination && trip.destination) {
      destination = trip.destination;
    }

    const travelMode = normalizeTravelMode(trip.travelMode || this.data.travelMode);
    this.setData({ totalDays, destination, travelMode, travelModeMeta: getTravelModeMeta(travelMode) });
    this._tripData = formattedTrip;
    this.loadDay(1);
  },

  async loadDay(dayIndex) {
    const trip = this._tripData;
    if (!trip || !trip.days) return;

    const dayData = trip.days[dayIndex - 1];
    if (!dayData || !dayData.points || dayData.points.length === 0) {
      wx.showToast({ title: '该天无行程点', icon: 'none' });
      return;
    }

    this.setData({
      loadingRoute: true,
      dayDate: dayData.date || ''
    });

    const city = this.resolveCity(dayData);
    this._resolvedCity = city; // 供 loadRealRoute 使用
    const startPoint = this.data.startPoint || '';
    const pointsWithCoords = [];

    for (let i = 0; i < dayData.points.length; i++) {
      const p = dayData.points[i];
      let lat = 0;
      let lng = 0;

      if (p.latitude && p.longitude) {
        lat = p.latitude;
        lng = p.longitude;
      } else {
        const searchCity = (dayIndex === 1 && i === 0 && startPoint) ? startPoint : city;

        // === POI 搜索：带城市 → 空则去城市全国搜 ===
        try {
          const pois = await tmap.searchPOI(p.name, searchCity);
          if (pois && pois.length > 0) {
            lng = pois[0].longitude;
            lat = pois[0].latitude;
          }
        } catch (e) {
          console.warn('POI搜索失败:', p.name, e);
        }

        // POI 带城市无结果 → 去掉城市全国搜（跨区域行程场景）
        if (!lat || !lng) {
          try {
            const pois2 = await tmap.searchPOI(p.name, '');
            if (pois2 && pois2.length > 0) {
              lng = pois2[0].longitude;
              lat = pois2[0].latitude;
              console.log('[POI降级] 全国搜索命中:', p.name, lat, lng);
            }
          } catch (e) {
            console.warn('POI全国搜索失败:', p.name, e);
          }
        }

        // === 地理编码：先不加城市直接搜景点名（更精准） → 搜不到再加城市消歧 ===
        if (!lat || !lng) {
          try {
            const geoResult = await tmap.geocode(p.name, '');
            if (geoResult && geoResult.lat && geoResult.lng) {
              lat = geoResult.lat;
              lng = geoResult.lng;
              console.log('[geocode] 全国搜索命中:', p.name, lat, lng);
            }
          } catch (e2) {
            console.warn('地理编码失败:', p.name, e2);
          }
        }

        // 全国搜无结果 → 加城市前缀兜底（适用于"人民公园"等常见地名需要消歧）
        if (!lat || !lng) {
          try {
            const geoResult2 = await tmap.geocode(p.name, searchCity);
            if (geoResult2 && geoResult2.lat && geoResult2.lng) {
              lat = geoResult2.lat;
              lng = geoResult2.lng;
            }
          } catch (e2) {
            console.warn('地理编码(带城市)失败:', p.name, e2);
          }
        }

        // === 地址编码（不带城市） ===
        if ((!lat || !lng) && p.address) {
          try {
            const geoResult = await tmap.geocode(p.address, '');
            if (geoResult && geoResult.lat && geoResult.lng) {
              lat = geoResult.lat;
              lng = geoResult.lng;
            }
          } catch (e3) {
            console.warn('地址编码失败:', p.address, e3);
          }
        }

        // === 兜底坐标 ===
        if (!lat || !lng) {
          const fallback = await mapUtils.getFallbackCoordsAsync(i, dayData.points.length, city);
          lat = fallback.lat;
          lng = fallback.lng;
          console.warn('使用兜底坐标:', p.name, lat, lng);
        }
      }

      pointsWithCoords.push({
        id: p.id || ('p' + i),
        name: p.name || ('途经点' + (i + 1)),
        arrivalTime: p.arrivalTime || '',
        duration: p.duration || 60,
        latitude: lat,
        longitude: lng,
        type: i === 0 ? 'start' : (i === dayData.points.length - 1 ? 'end' : 'spot')
      });
    }

    // 缓存解析出的坐标到 _tripData（下次打开不再调 API）+ 批量保存到后端
    this._cacheSpotCoords(dayData.points, pointsWithCoords);

    const centerLat = (pointsWithCoords[0] && pointsWithCoords[0].latitude) || 24.4798;
    const centerLng = (pointsWithCoords[0] && pointsWithCoords[0].longitude) || 118.0894;

    const markers = this.buildMarkers(pointsWithCoords);

    const polylines = pointsWithCoords.length >= 2 ? this.buildStraightPolyline(pointsWithCoords) : [];

    let totalDistance = 0;
    for (let i = 1; i < pointsWithCoords.length; i++) {
      totalDistance += tmap.calculateDistance(
        pointsWithCoords[i - 1].longitude, pointsWithCoords[i - 1].latitude,
        pointsWithCoords[i].longitude, pointsWithCoords[i].latitude
      );
    }

    this.setData({
      currentDay: dayIndex,
      mapData: { longitude: centerLng, latitude: centerLat, scale: 14 },
      markers,
      polylines,
      routeStats: {
        distance: (totalDistance / 1000).toFixed(1),
        duration: this.estimateDuration(pointsWithCoords),
        points: pointsWithCoords
      },
      loaded: true,
      routeLoaded: false
    });

    // 强制地图适配所有点位
    setTimeout(() => {
      this.fitMapToPoints(pointsWithCoords);
    }, 300);

    // 加载腾讯地图真实路线
    if (pointsWithCoords.length >= 2) {
      this.loadRealRoute(pointsWithCoords);
    } else {
      this.setData({ loadingRoute: false });
    }
  },

  buildMarkers(points) {
    return points.map((p, idx) => {
      const label = idx === 0 ? '起'
        : idx === points.length - 1 ? '终'
        : String(idx);

      return {
        id: idx + 1,
        latitude: p.latitude,
        longitude: p.longitude,
        iconPath: this.getMarkerIcon(p.type),
        width: 28,
        height: 36,
        label: {
          content: label,
          color: '#fff',
          fontSize: 10,
          bgColor: idx === 0 ? '#52c41a' : (idx === points.length - 1 ? '#f5222d' : '#3A7BD5'),
          padding: 2,
          borderRadius: 8,
          textAlign: 'center'
        }
      };
    });
  },

  // 备用直线连线（在所有情况下都会显示）
  buildStraightPolyline(points) {
    return mapUtils.buildStraightPolyline(points);
  },

  loadRealRoute(points) {
    const origin = `${points[0].longitude},${points[0].latitude}`;
    const dest = `${points[points.length - 1].longitude},${points[points.length - 1].latitude}`;
    const waypoints = points.slice(1, -1).map(p => `${p.longitude},${p.latitude}`);
    const mode = this.data.travelMode;

    this.setData({ loadingRoute: true });

    tmap.getRoute(origin, dest, waypoints, mode, this._resolvedCity || '')
      .then((route) => {

        if (route.paths && route.paths.length > 0) {
          // 缓存原始路径数据，供备选路线切换使用
          this._rawPaths = route.paths;

          // 存储所有路线元数据
          const allPathsData = route.paths.map((path, idx) => ({
            index: idx,
            distance: path.distance,
            duration: path.duration,
            label: this.getPathLabel(idx, path, route.paths.length),
            distanceText: (path.distance / 1000).toFixed(1) + 'km'
          }));

          this.setData({
            allPaths: allPathsData,
            totalPaths: route.paths.length,
            currentPathIndex: 0
          });

          // 渲染当前选中的路线
          this.renderPath(route.paths[0], mode, points);

        } else {
          this.setData({ loadingRoute: false });
          wx.showToast({ title: '未找到路线', icon: 'none' });
        }
      })
      .catch((error) => {
        console.error('腾讯地图路径规划失败:', error);
        this.setData({ loadingRoute: false });

        const errMsg = (error && error.message) || '';
        if (errMsg.includes('调用量已达到上限') || errMsg.includes('调用量超限')) {
          wx.showToast({ title: 'API 今日调用已满，显示直线连接', icon: 'none', duration: 3000 });
        } else if (errMsg.includes('签名') || errMsg.includes('key')) {
          wx.showToast({ title: 'API Key 无效，显示直线连接', icon: 'none', duration: 3000 });
        } else {
          wx.showToast({ title: '路线规划失败，显示直线连接', icon: 'none', duration: 2500 });
        }
      });
  },

  // 渲染单条路线
  renderPath(path, mode, points) {

    var coordPoints;

    // 检查 polyline 是否已在后端解码（非驾车模式分段合并后返回的是已解码坐标）
    if (path.polyline_decoded && path.polyline && path.polyline.length > 0) {
      // 后端已解码：[ [lat, lng], [lat, lng], ... ] → 转换为 {latitude, longitude}
      coordPoints = this.convertDecodedPolyline(path.polyline);
    } else {
      // 驾车模式：原始压缩格式，需要前端解码
      coordPoints = this.decodePolyline(path.polyline);
    }

    if (coordPoints.length === 0) {
      console.error('❌ polyline 解码后坐标为空');
      this.setData({ loadingRoute: false });
      return;
    }

    const style = this.getPolylineStyle(mode);

    // 如果有 steps 且包含 polyline_idx，则按路段分段渲染不同颜色
    let polylines;
    if (path.steps && path.steps.length > 0 && this.hasPolylineIdx(path.steps)) {
      polylines = this.buildSegmentedPolylines(coordPoints, path.steps, style, mode);
    } else {
      // 单色整条路线
      polylines = [{
        points: coordPoints,
        color: style.color,
        width: style.width,
        arrowLine: style.arrowLine || false,
        dottedLine: style.dottedLine || false,
        borderColor: style.borderColor || '',
        borderWidth: style.borderWidth || 0
      }];
    }


    // 保留途经点标记
    var oldMarkers = this.data.markers || [];
    var baseMarkers = [];
    for (var mi = 0; mi < oldMarkers.length; mi++) {
      if (oldMarkers[mi].id < 1000) {
        baseMarkers.push(oldMarkers[mi]);
      }
    }

    this.setData({
      markers: baseMarkers,
      polylines,
      routeLoaded: true,
      loadingRoute: false,
      'routeStats.distance': (path.distance / 1000).toFixed(1),
      'routeStats.duration': this.formatDuration(path.duration)
    });


    setTimeout(function () {
      this.fitMapToPoints(this.data.routeStats.points);
    }.bind(this), 100);
  },

  // 检查 steps 是否有 polyline_idx
  hasPolylineIdx(steps) {
    return mapUtils.hasPolylineIdx(steps);
  },

  // 根据 steps 的 polyline_idx 分段渲染路线
  buildSegmentedPolylines(coordPoints, steps, baseStyle, mode) {
    return mapUtils.buildSegmentedPolylines(coordPoints, steps, baseStyle, mode);
  },

  // 获取路段颜色方案
  getRoadColors(mode) {
    return mapUtils.getRoadColors(mode);
  },

  // 颜色加深
  darkenColor(hex, factor) {
    return mapUtils.darkenColor(hex, factor);
  },

  // 获取路线标签
  getPathLabel(index, path, total) {
    return mapUtils.getPathLabel(index, path, total);
  },

  // 切换备选路线
  switchPath(e) {
    var index = parseInt(e.detail.index);
    if (index === this.data.currentPathIndex) return;

    var points = this.data.routeStats.points;
    var mode = this.data.travelMode;

    // 优先使用缓存的路径数据
    if (this._rawPaths && this._rawPaths.length > index) {
      this.setData({
        currentPathIndex: index,
        showPathPicker: false
      });
      this.renderPath(this._rawPaths[index], mode, points);
      return;
    }

    // 缓存不存在，重新请求
    this.setData({
      currentPathIndex: index,
      showPathPicker: false,
      loadingRoute: true
    });

    if (points && points.length >= 2) {
      var origin = points[0].longitude + ',' + points[0].latitude;
      var dest = points[points.length - 1].longitude + ',' + points[points.length - 1].latitude;
      var waypoints = points.slice(1, -1).map(function(p) {
        return p.longitude + ',' + p.latitude;
      });
      var self = this;

      tmap.getRoute(origin, dest, waypoints, mode, this._resolvedCity || '')
        .then(function(route) {
          if (route.paths && route.paths.length > index) {
            self._rawPaths = route.paths;
            self.renderPath(route.paths[index], mode, points);
          } else {
            self.setData({ loadingRoute: false });
          }
        })
        .catch(function() {
          self.setData({ loadingRoute: false });
        });
    }
  },

  // 显示/隐藏路线选择器
  togglePathPicker() {
    this.setData({ showPathPicker: !this.data.showPathPicker });
  },

  // 将后端已解码的 [[lat, lng], ...] 转换为 [{latitude, longitude}, ...]
  convertDecodedPolyline(decoded) {
    return mapUtils.convertDecodedPolyline(decoded);
  },

  // 解码腾讯地图差分编码的 polyline 数组
  decodePolyline(encoded) {
    return mapUtils.decodePolyline(encoded);
  },

  // 根据出行方式获取 polyline 样式
  getPolylineStyle(mode) {
    return mapUtils.getPolylineStyle(mode);
  },

  getMarkerIcon(type) {
    return mapUtils.getMarkerIcon(type);
  },

  getFallbackCoords(index, total) {
    return mapUtils.getFallbackCoords(index, total);
  },

  estimateDuration(points) {
    return mapUtils.estimateDuration(points);
  },

  formatDuration(minutes) {
    return mapUtils.formatDuration(minutes);
  },

  // ==================== 地图事件 ====================

  onMarkerTap(e) {
    const markerId = e.detail.markerId;
    const point = this.data.routeStats.points[markerId - 1];
    if (point) {
      wx.showToast({
        title: point.name + (point.arrivalTime ? ' ' + point.arrivalTime : ''),
        icon: 'none',
        duration: 2000
      });
    }
  },

  onRegionChange(e) {
    if (e.type === 'end') {
      if (this.data.editMode) {
        this.mapContext.getCenterLocation({
          success: function (res) {
          }
        });
      }
    }
  },

  // ==================== 视图控制 ====================

  togglePanel() {
    this.setData({ panelCollapsed: !this.data.panelCollapsed });
  },

  onPanelTouchStart(e) {
    this._startY = e.touches[0].clientY;
    this._startTranslate = this.data.panelTranslate;
    this.setData({ panelDragging: true });
  },

  onPanelTouchMove(e) {
    if (!this._startY) return;
    const deltaY = e.touches[0].clientY - this._startY;
    const translate = this._startTranslate + deltaY;
    // 限制拖动范围
    const clampedTranslate = Math.max(-100, Math.min(400, translate));
    this.setData({ panelTranslate: clampedTranslate });
    // 阻止默认滚动行为，防止事件传播到地图
    return false;
  },

  onPanelTouchEnd(e) {
    const { panelTranslate, panelCollapsed } = this.data;
    const threshold = 60;
    let collapsed = panelCollapsed;

    if (panelCollapsed && panelTranslate < -threshold) {
      collapsed = false;
    } else if (!panelCollapsed && panelTranslate > threshold) {
      collapsed = true;
    }

    this._startY = null;
    this._startTranslate = 0;
    this.setData({
      panelCollapsed: collapsed,
      panelDragging: false,
      panelTranslate: 0
    });
  },

  switchDay(e) {
    const day = parseInt(e.currentTarget.dataset.day);
    if (day !== this.data.currentDay) {
      this.loadDay(day);
    }
  },

  recenterMap() {
    if (!this.data.markers || this.data.markers.length === 0) return;
    const bounds = this.calculateBounds(this.data.routeStats.points);
    if (bounds) {
      this.mapContext.includePoints({
        points: bounds,
        padding: [60, 60, 60, 60]
      });
    }
  },

  calculateBounds(points) {
    if (!points || points.length === 0) return null;
    return points.map(p => ({
      latitude: p.latitude,
      longitude: p.longitude
    }));
  },

  // 地图缩放到适配所有点位
  fitMapToPoints(points) {
    mapUtils.fitMapToPoints('map', points);
  },

  toggleEditMode() {
    const newMode = !this.data.editMode;
    this.setData({ editMode: newMode });
    wx.showToast({
      title: newMode ? '编辑模式已开启' : '编辑模式已关闭',
      icon: 'none'
    });
  },

  onWaypointTap(e) {
    if (this.data.editMode) {
      this.editWaypoint(e.detail.index);
    }
  },

  // ==================== 路线计算 ====================

  calculateRoute() {
    const points = this.data.routeStats.points || [];
    if (points.length < 2) {
      wx.showToast({ title: '至少需要 2 个途经点', icon: 'none' });
      return;
    }

    this.setData({ loadingRoute: true });

    // 更新标记
    const markers = this.buildMarkers(points);
    this.setData({ markers });

    // 重新规划
    const origin = `${points[0].longitude},${points[0].latitude}`;
    const destination = `${points[points.length - 1].longitude},${points[points.length - 1].latitude}`;
    const waypoints = points.slice(1, -1).map(p => `${p.longitude},${p.latitude}`);

    tmap.getRoute(origin, destination, waypoints, this.data.travelMode, this._resolvedCity || '')
      .then((route) => {
        if (route.paths && route.paths.length > 0) {
          // 缓存原始路径数据，供备选路线切换使用
          this._rawPaths = route.paths;

          // 存储路线元数据
          const allPathsData = route.paths.map((path, idx) => ({
            index: idx,
            distance: path.distance,
            duration: path.duration,
            label: this.getPathLabel(idx, path, route.paths.length)
          }));

          this.setData({
            allPaths: allPathsData,
            totalPaths: route.paths.length,
            currentPathIndex: 0
          });

          // 渲染第一条路线
          this.renderPath(route.paths[0], this.data.travelMode, points);

        } else {
          // 未返回路线，用直线代替
          const fallback = this.buildStraightPolyline(points);
          this.setData({ polylines: fallback, loadingRoute: false, totalPaths: 0, allPaths: [] });
          wx.showToast({ title: '未找到路线，显示直线连接', icon: 'none' });
        }
      })
      .catch((error) => {
        console.error('路径规划失败:', error);
        // 用直线代替
        const fallback = this.buildStraightPolyline(points);
        this.setData({ polylines: fallback, loadingRoute: false, totalPaths: 0, allPaths: [] });

        const errMsg = (error && error.message) || '';
        if (errMsg.includes('调用量已达到上限') || errMsg.includes('调用量超限')) {
          wx.showToast({ title: 'API 今日调用已满，显示直线连接', icon: 'none', duration: 3000 });
        } else {
          wx.showToast({ title: '路线计算失败，显示直线连接', icon: 'none', duration: 2500 });
        }
      });
  },

  // ==================== 途经点操作 ====================

  addWaypoint() {
    this.setData({
      showAddModal: true,
      addPointName: ''
    });
  },

  onAddPointInput(e) {
    this.setData({ addPointName: e.detail.value });
  },

  confirmAddPoint() {
    const name = this.data.addPointName.trim();
    if (!name) {
      wx.showToast({ title: '请输入地点名称', icon: 'none' });
      return;
    }

    this.setData({ showAddModal: false });

    // POI 搜索优先（景点/地标更精准）
    const city = this._resolvedCity || '';

    const resolveCoords = tmap.searchPOI(name, city).then((pois) => {
      if (pois && pois.length > 0 && pois[0].latitude) {
        return { lat: pois[0].latitude, lng: pois[0].longitude };
      }
      // 降级：地理编码（先全国搜，再带城市消歧）
      return tmap.geocode(name, '').then((geoResult) => {
        if (geoResult && geoResult.lat && geoResult.lng) {
          return { lat: geoResult.lat, lng: geoResult.lng };
        }
        return tmap.geocode(name, city);
      }).then((geoResult) => {
        if (geoResult && geoResult.lat && geoResult.lng) {
          return { lat: geoResult.lat, lng: geoResult.lng };
        }
        return { lat: 0, lng: 0 };
      });
    });

    resolveCoords.then(({ lat, lng }) => {
      if (!lat || !lng) {
        const fallback = this.getFallbackCoords(
          this.data.routeStats.points.length,
          this.data.routeStats.points.length + 1
        );
        lat = fallback.lat;
        lng = fallback.lng;
      }

        const newPoint = {
          id: 'p_' + Date.now(),
          name,
          latitude: lat,
          longitude: lng,
          arrivalTime: '12:00',
          duration: 60,
          type: 'spot'
        };

        const points = [...(this.data.routeStats.points || [])];
        points.splice(points.length - 1, 0, newPoint);

        this.setData({ 'routeStats.points': points });
        this.calculateRoute();
      })
      .catch(() => {
        wx.showToast({ title: '搜索失败，请重试', icon: 'none' });
      });
  },

  cancelAddPoint() {
    this.setData({ showAddModal: false });
  },

  editWaypoint(index) {
    const point = this.data.routeStats.points[index];
    if (!point) return;

    this.setData({
      showEditModal: true,
      editingIndex: index,
      editPointName: point.name,
      editPointTime: point.arrivalTime || '12:00',
      editPointDuration: point.duration || 60
    });
  },

  onEditNameInput(e) {
    this.setData({ editPointName: e.detail.value });
  },

  onEditTimeInput(e) {
    this.setData({ editPointTime: e.detail.value });
  },

  onEditDurationInput(e) {
    this.setData({ editPointDuration: parseInt(e.detail.value) || 60 });
  },

  confirmEditPoint() {
    const index = this.data.editingIndex;
    const points = [...(this.data.routeStats.points || [])];
    if (index < 0 || index >= points.length) return;

    points[index] = {
      ...points[index],
      name: this.data.editPointName,
      arrivalTime: this.data.editPointTime,
      duration: this.data.editPointDuration
    };

    this.setData({
      'routeStats.points': points,
      showEditModal: false,
      editingIndex: -1
    });

    this.calculateRoute();
  },

  cancelEditPoint() {
    this.setData({
      showEditModal: false,
      editingIndex: -1
    });
  },

  removeWaypoint(e) {
    const index = e.detail.index;
    const points = (this.data.routeStats.points || []).filter((_, i) => i !== index);
    this.setData({ 'routeStats.points': points });

    if (points.length >= 1) {
      const markers = this.buildMarkers(points);
      this.setData({ markers });
    }

    if (points.length >= 2) {
      this.calculateRoute();
    } else {
      this.setData({ polylines: [], routeLoaded: false });
      wx.showToast({ title: '已删除，请至少保留 2 个点', icon: 'none' });
    }
  },

  moveWaypointUp(e) {
    const index = e.detail.index;
    if (index <= 0) return;
    const points = [...(this.data.routeStats.points || [])];
    [points[index - 1], points[index]] = [points[index], points[index - 1]];
    this.setData({ 'routeStats.points': points });
    this.calculateRoute();
  },

  moveWaypointDown(e) {
    const index = e.detail.index;
    const points = [...(this.data.routeStats.points || [])];
    if (index >= points.length - 1) return;
    [points[index], points[index + 1]] = [points[index + 1], points[index]];
    this.setData({ 'routeStats.points': points });
    this.calculateRoute();
  },

  // ==================== 去导航 ====================

  goToNavigation() {
    const points = this.data.routeStats.points;
    if (!points || points.length < 2) {
      wx.showToast({ title: '至少需要 2 个途经点', icon: 'none' });
      return;
    }

    wx.showActionSheet({
      itemList: ['高德地图（推荐）', '腾讯地图'],
      success: (res) => {
        if (res.tapIndex === 0) {
          this.openAmapNavigation(points);
        } else {
          this.openTencentNavigation(points);
        }
      }
    });
  },

  // 高德地图导航（H5 中转 → 唤起高德地图 App，支持全部途经点）
  openAmapNavigation(points) {
    mapUtils.openAmapNavigation(points, this.data.travelMode, NAV_BRIDGE_HOST);
  },

  // 腾讯地图导航（使用路线规划插件，附带途经点）
  openTencentNavigation(points) {
    mapUtils.openTencentNavigation(points, this.data.travelMode, app.globalData.tmapKey);
  },

  // ==================== 导航 ====================

  calculateArrivalTime(index) {
    const baseHours = 9 + index * 2;
    const hours = Math.floor(baseHours);
    const minutes = (baseHours % 1) * 60;
    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
  },

  // ==================== 工具方法 ====================

  noop() {
    // 空函数，用于阻止事件冒泡
  },

  // ==================== 腾讯地图路线规划插件 ====================

  openRoutePlanner() {
    const points = this.data.routeStats.points;
    if (!points || points.length < 2) {
      wx.showToast({ title: '至少需要 2 个途经点', icon: 'none' });
      return;
    }

    const key = app.globalData.tmapKey;
    const referer = '拾路派';
    const modeMap = { drive: 'driving', walk: 'walking', transit: 'transit' };
    const mode = modeMap[this.data.travelMode] || 'driving';

    const startPoint = JSON.stringify({
      name: points[0].name,
      latitude: points[0].latitude,
      longitude: points[0].longitude
    });

    const endPoint = JSON.stringify({
      name: points[points.length - 1].name,
      latitude: points[points.length - 1].latitude,
      longitude: points[points.length - 1].longitude
    });

    wx.navigateTo({
      url: `plugin://route-plan/index?key=${key}&referer=${referer}&startPoint=${startPoint}&endPoint=${endPoint}&mode=${mode}`
    });
  },

  // ==================== 导航 ====================

  startNavigation() {
    const points = this.data.routeStats.points;
    if (!points || points.length === 0) {
      wx.showToast({ title: '没有可导航的点', icon: 'none' });
      return;
    }

    wx.showActionSheet({
      itemList: ['导航到起点', '导航到终点'],
      success: (res) => {
        const target = res.tapIndex === 0 ? points[0] : points[points.length - 1];
        wx.openLocation({
          latitude: target.latitude,
          longitude: target.longitude,
          name: target.name,
          scale: 16
        });
      }
    });
  },

  toggleTraffic() {
    wx.showToast({ title: '路况显示已切换', icon: 'none' });
  },

  // ==================== 定位与天气 ====================

  getCurrentLocation() {
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        tmap.reverseGeocode(res.longitude, res.latitude)
          .then((result) => {
            const city = (result.address_component && result.address_component.city) || '';
            const district = (result.address_component && result.address_component.district) || '';
            const locationName = city ? (city + (district ? ' · ' + district : '')) : '当前位置';
            const tripName = this.data.destination ? ' · ' + this.data.destination + '游' : '';
            this.setData({ currentLocation: locationName + tripName });
          })
          .catch(() => {
            this.setData({ currentLocation: this.data.destination || '厦门' });
          });
        // 使用坐标获取天气
        this.fetchWeather(res.latitude, res.longitude);
      },
      fail: () => {
        this.setData({ currentLocation: this.data.destination || '厦门' });
        // 定位失败降级：尝试用城市名查天气
        this.fetchWeatherByCity();
      }
    });
  },

  fetchWeather(latitude, longitude) {
    // L9: 使用 request 调用（request 已自动注入 X-User-Id/X-Token）
    request({ url: `/api/map/weather?latitude=${latitude}&longitude=${longitude}`, method: 'GET' })
      .then((data) => {
        if (data) {
          this.applyWeatherData(data);
        } else {
          this.fetchWeatherByCity();
        }
      })
      .catch(() => {
        this.fetchWeatherByCity();
      });
  },

  fetchWeatherByCity() {
    // 用坐标 0,0 尝试，由天气API根据IP/城市降级
    const dest = this.data.destination || '厦门';
    // 先用目的地城市的地理编码获取坐标
    tmap.geocode(dest).then((geoData) => {
      if (geoData && geoData.lat && geoData.lng) {
        return request({
          url: `/api/map/weather?latitude=${geoData.lat}&longitude=${geoData.lng}`,
          method: 'GET'
        });
      }
      return null;
    }).then((data) => {
      if (data) this.applyWeatherData(data);
    }).catch((err) => {
      console.warn('天气获取失败:', err);
    });
  },

  applyWeatherData(data) {
    // 腾讯地图天气响应格式：{ realtime: [{ infos: { weather, temperature, ... } }] }
    let now = {};
    if (data.realtime && Array.isArray(data.realtime) && data.realtime.length > 0) {
      now = data.realtime[0].infos || data.realtime[0] || {};
    } else if (data.obs) {
      now = data.obs;
    }
    const temp = now.temperature || now.temp || '';
    const desc = now.weather || '';
    let iconPath = '/images/icons/scenic.svg';
    if (desc.includes('雨')) iconPath = '/images/icons/hiking-warning.svg';
    else if (desc.includes('云') || desc.includes('阴')) iconPath = '/images/icons/scenic.svg';
    else if (desc.includes('雪')) iconPath = '/images/icons/hiking-warning.svg';
    else if (desc.includes('雾')) iconPath = '/images/icons/hiking-warning.svg';
    this.setData({
      'weather.temp': temp,
      'weather.desc': desc,
      'weather.iconPath': iconPath
    });
  },

  switchLocation() {
    wx.showActionSheet({
      itemList: ['查看全部途经点', '重新定位'],
      success: (res) => {
        if (res.tapIndex === 0) {
          this.recenterMap();
        } else {
          this.getCurrentLocation();
          wx.showToast({ title: '正在重新定位...', icon: 'none' });
        }
      }
    });
  },

  // ==================== 分享 ====================

  onShareAppMessage() {
    const dest = this.data.destination;
    const stats = this.data.routeStats;
    let title = '拾路派 - 路线规划';
    if (dest) title += ' - ' + dest;
    if (stats.distance) title += ' 全程' + stats.distance + 'km';
    return share.shareToFriend({
      title,
      path: '/pages/map/map?tripId=' + this.data.tripId + '&destination=' + encodeURIComponent(dest || '')
    });
  },

  onShareTimeline() {
    const dest = this.data.destination;
    let title = '拾路派 - 路线规划';
    if (dest) title += ' - ' + dest;
    return share.shareToTimeline({
      title,
      query: 'tripId=' + this.data.tripId + '&destination=' + encodeURIComponent(dest || '')
    });
  }
});
