const tmap = require('../../utils/tmap');
const hikingAPI = require('../../api/hiking');
const tripAPI = require('../../api/trip');
const { themeMap, applyTheme } = require('../../utils/theme');

const displayMetric = (value) => (value === null || value === undefined || value === '' ? '待补充' : value);

function parseDurationMinutes(value) {
  if (!value) return 0;
  const hours = /([0-9.]+)\s*(小时|h)/.exec(value);
  const minutes = /([0-9.]+)\s*(分钟|min)/.exec(value);
  return Math.round((hours ? Number(hours[1]) * 60 : 0) + (minutes ? Number(minutes[1]) : 0));
}

function parseJson(value) {
  if (!value || typeof value !== 'string') return value;
  try { return JSON.parse(value); } catch (err) { return value; }
}

Page({
  data: {
    ...themeMap,
    plan: null,
    routeOptions: [],
    selectedRouteId: '',
    mapData: { latitude: 30.153, longitude: 120.129, scale: 13 },
    markers: [],
    polylines: [],
    status: 'planned',
    elapsedText: '00:00',
    offlineSaved: false,
    preparation: [],
    safetyTips: [],
    segments: []
  },

  onLoad(options) {
    applyTheme(this);
    this.options = options || {};
    if (this.options.tripId) {
      this.loadTripRoutes(this.options.tripId);
      return;
    }
    if (this.options.routeId) {
      this.loadRoute(this.options.routeId, { tripId: this.options.tripId });
      return;
    }
    const plan = wx.getStorageSync('activeHikingPlan');
    if (!plan) {
      wx.showToast({ title: '暂未找到徒步路线', icon: 'none' });
      return;
    }
    this.applyPlan(plan, plan.segments || []);
  },

  onShow() { applyTheme(this); },
  onUnload() { this.stopTimer(); },

  async loadTripRoutes(tripId) {
    wx.showLoading({ title: '加载徒步方案' });
    try {
      const [routes, trip] = await Promise.all([
        hikingAPI.getRoutesByTrip(tripId),
        tripAPI.getTripDetail(tripId)
      ]);
      if (!routes || !routes.length) throw new Error('徒步路线仍在生成中');
      this.trip = trip || { id: tripId };
      this.setData({
        routeOptions: routes.map((item, index) => ({
          id: item.id,
          name: item.routeName || `方案 ${index + 1}`,
          summary: `${displayMetric(item.totalDistance)}km · ${displayMetric(item.totalAscent)}m 爬升`
        }))
      });
      await this.loadRoute(routes[0].id, { tripId, trip: this.trip, route: routes[0] });
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '加载路线失败', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  async loadRoute(routeId, context = {}) {
    try {
      const [detail, segments] = await Promise.all([
        context.route ? Promise.resolve(context.route) : hikingAPI.getRouteDetail(routeId),
        hikingAPI.getRouteSegments(routeId)
      ]);
      const plan = this.normalizePlan(detail, segments || [], context.trip || this.trip || {}, context.tripId);
      this.applyPlan(plan, segments || []);
      this.setData({ selectedRouteId: routeId });
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '读取路线详情失败', icon: 'none' });
    }
  },

  normalizePlan(route, segments, trip, tripId) {
    const first = segments[0] || {};
    const last = segments[segments.length - 1] || {};
    const totalTime = route.totalTime || route.walkTime || '';
    const durationMin = parseDurationMinutes(totalTime);
    const routeType = route.routeType || '';
    return {
      id: route.id,
      routeId: route.id,
      tripId: tripId || route.tripPlanId || trip.id || '',
      tripStatus: trip.status || 'DRAFT',
      status: trip.status || 'DRAFT',
      destination: trip.destination || '徒步路线',
      startPoint: first.startPointName || trip.startPoint || '起点待确认',
      endPoint: last.endPointName || trip.endPoint || '终点待确认',
      routeType,
      routeTypeLabel: routeType || '路线类型待补充',
      preference: route.routeName || 'AI 徒步方案',
      distanceKm: displayMetric(route.totalDistance),
      elevationGain: displayMetric(route.totalAscent),
      elevationDescent: displayMetric(route.totalDescent),
      durationText: totalTime || '时长待补充',
      durationMin,
      difficulty: route.difficulty ? `${route.difficulty} 级难度` : '难度待补充',
      gear: parseJson(route.gearJson),
      safety: parseJson(route.safetyJson),
      segmentsRaw: segments
    };
  },

  applyPlan(plan, rawSegments) {
    const pageStatus = plan.status === 'ONGOING' ? 'hiking' : (plan.status === 'COMPLETED' ? 'completed' : 'planned');
    const normalized = {
      ...plan,
      turnaroundTime: this.getTurnaroundTime(plan.durationMin)
    };
    this.setData({
      plan: normalized,
      status: pageStatus,
      segments: this.buildSegments(rawSegments, normalized),
      preparation: this.buildPreparation(normalized.gear),
      safetyTips: this.buildSafetyTips(normalized.safety, rawSegments)
    });
    this.persistPlan(normalized);
    this.loadTrailPoints(normalized, rawSegments);
  },

  getTurnaroundTime(durationMin) {
    if (!durationMin) return '请出发前确认';
    const latest = new Date(Date.now() + Math.max(90, durationMin / 2 + 75) * 60000);
    return `${String(latest.getHours()).padStart(2, '0')}:${String(latest.getMinutes()).padStart(2, '0')}`;
  },

  buildSegments(rawSegments, plan) {
    if (!rawSegments || !rawSegments.length) {
      return [{ title: '路线分段待补充', meta: '当前方案尚未返回可用的路段数据', icon: '待' }];
    }
    return rawSegments.map((item, index) => {
      const meta = [
        item.distance !== null && item.distance !== undefined ? `${item.distance}km` : '',
        item.ascent !== null && item.ascent !== undefined ? `爬升 ${item.ascent}m` : '',
        item.roadType || '',
        item.slope || '',
        item.riskTip ? `注意：${item.riskTip}` : ''
      ].filter(Boolean).join(' · ');
      return { title: item.name || `第 ${index + 1} 段`, meta: meta || '路段信息待补充', icon: String(index + 1) };
    });
  },

  buildPreparation(gear) {
    const items = Array.isArray(gear) ? gear : (gear && (gear.items || gear.gear || gear.essentials));
    if (Array.isArray(items) && items.length) {
      return items.slice(0, 8).map((item) => ({ label: typeof item === 'string' ? item : (item.name || item.item || item.label), done: false }));
    }
    return [
      { label: '确认天气与最晚折返时间', done: false },
      { label: '准备饮水、能量补给与照明', done: false },
      { label: '下载离线地图并充满电', done: false },
      { label: '将路线分享给同行人或紧急联系人', done: false }
    ];
  },

  buildSafetyTips(safety, rawSegments) {
    const fromSafety = Array.isArray(safety) ? safety : (safety && (safety.tips || safety.items || safety.warnings));
    const tips = Array.isArray(fromSafety) ? fromSafety.map((item) => typeof item === 'string' ? item : (item.content || item.tip || item.name)) : [];
    rawSegments.forEach((item) => { if (item.riskTip) tips.push(item.riskTip); });
    return [...new Set(tips.filter(Boolean))].slice(0, 5);
  },

  async selectRoute(e) {
    const routeId = e.currentTarget.dataset.id;
    if (!routeId || routeId === this.data.selectedRouteId) return;
    wx.showLoading({ title: '切换方案' });
    await this.loadRoute(routeId, { tripId: this.data.plan.tripId, trip: this.trip });
    wx.hideLoading();
  },

  async loadTrailPoints(plan, rawSegments) {
    try {
      const first = rawSegments[0] || {};
      const last = rawSegments[rawSegments.length - 1] || {};
      const start = first.startLat && first.startLng
        ? { latitude: first.startLat, longitude: first.startLng }
        : await tmap.geocode(plan.startPoint, plan.destination);
      const end = last.endLat && last.endLng
        ? { latitude: last.endLat, longitude: last.endLng }
        : await tmap.geocode(plan.endPoint, plan.destination);
      if (!start || !end) return;
      const points = [
        { latitude: Number(start.latitude || start.lat), longitude: Number(start.longitude || start.lng) },
        { latitude: Number(end.latitude || end.lat), longitude: Number(end.longitude || end.lng) }
      ];
      const center = { latitude: (points[0].latitude + points[1].latitude) / 2, longitude: (points[0].longitude + points[1].longitude) / 2 };
      this.setData({
        mapData: { ...center, scale: 13 },
        markers: [
          { id: 1, ...points[0], title: plan.startPoint, width: 28, height: 28, callout: { content: '起点', display: 'BYCLICK', padding: 6, borderRadius: 8 } },
          { id: 2, ...points[1], title: plan.endPoint, width: 28, height: 28, callout: { content: '终点', display: 'BYCLICK', padding: 6, borderRadius: 8 } }
        ],
        polylines: [{ points, color: '#2EC4B6', width: 6, arrowLine: true, borderColor: '#ffffff', borderWidth: 2 }]
      });
    } catch (err) {
      console.warn('[hiking-route] load trail points failed', err);
    }
  },

  togglePreparation(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ [`preparation[${index}].done`]: !this.data.preparation[index].done });
  },

  saveOffline() {
    this.setData({ offlineSaved: true });
    wx.showToast({ title: '已保存路线摘要', icon: 'success' });
  },

  startHiking() {
    const plan = this.data.plan;
    if (!plan || this.data.status === 'hiking') return;
    const start = () => tripAPI.startTrip(plan.tripId);
    const ensureSaved = plan.tripStatus === 'SAVED' ? Promise.resolve() : tripAPI.saveTrip(plan.tripId);
    if (!plan.tripId) {
      wx.showToast({ title: '行程信息缺失，请重新生成路线', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '开启徒步' });
    ensureSaved.then(start).then((trip) => {
      this.setData({ status: 'hiking' });
      this.persistPlan({ status: 'ONGOING', tripStatus: (trip && trip.status) || 'ONGOING' });
      this.startedAt = Date.now();
      this.timer = setInterval(() => {
        const elapsed = Math.floor((Date.now() - this.startedAt) / 1000);
        this.setData({ elapsedText: `${String(Math.floor(elapsed / 60)).padStart(2, '0')}:${String(elapsed % 60).padStart(2, '0')}` });
      }, 1000);
      wx.showToast({ title: '徒步已开始', icon: 'success' });
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '开启失败，请重试', icon: 'none' });
    }).finally(() => wx.hideLoading());
  },

  finishHiking() {
    wx.showModal({
      title: '结束本次徒步？',
      content: '结束后会同步保存本次行程状态。',
      success: (res) => {
        if (!res.confirm) return;
        wx.showLoading({ title: '保存中' });
        tripAPI.completeTrip(this.data.plan.tripId).then((trip) => {
          this.stopTimer();
          this.setData({ status: 'completed' });
          this.persistPlan({ status: 'COMPLETED', tripStatus: (trip && trip.status) || 'COMPLETED', completedAt: Date.now() });
          wx.showToast({ title: '徒步已完成', icon: 'success' });
        }).catch((err) => {
          wx.showToast({ title: (err && err.message) || '保存失败，请重试', icon: 'none' });
        }).finally(() => wx.hideLoading());
      }
    });
  },

  stopTimer() { if (this.timer) clearInterval(this.timer); this.timer = null; },

  persistPlan(patch) {
    const plan = { ...this.data.plan, ...patch };
    wx.setStorageSync('activeHikingPlan', plan);
    // 老版本的本地草稿仍可恢复；后端行程不再写入该列表，避免“我的行程”重复展示。
    if (!plan.tripId) {
      const plans = wx.getStorageSync('myHikingPlans') || [];
      wx.setStorageSync('myHikingPlans', [plan, ...plans.filter((item) => item.id !== plan.id)]);
    }
    this.setData({ plan });
  },

  adjustWithAI() {
    wx.setStorageSync('pendingHikingAdjustment', this.data.plan);
    wx.navigateBack();
  }
});
