const app = getApp();
const api = require('../../utils/api');
const cityApi = require('../../api/city');
const cache = require('../../utils/cache');
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');
const auth = require('../../utils/auth');
const { handleNotLogin } = require('../../utils/request');

Page({
  _hasLoaded: false,
  _lastScrollTop: 0,
  _preserveScrollOnShow: false,

  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    loading: false,
    keyword: '',
    showSearchBar: false,
    currentCategory: 'recommend',
    categoryTabs: [
      { key: 'recommend', label: '推荐' },
      { key: 'latest', label: '最新' }
    ],
    hotTags: [],
    selectedTag: '',

    // 城市选择（多选）
    selectedCities: [],               // 已选城市 [{id, name, pinyin}]
    selectedCityNames: [],            // 已选城市名称（用于显示和接口传参）
    selectedCityIds: [],              // 已选城市 ID（用于模板判断选中状态）

    // 城市弹窗
    showCityPopup: false,
    cityData: null,                   // { hotCities, groupCities, totalCount }
    currentLocation: null,            // { id, name } GPS定位城市

    // 天数下拉
    daysOptions: [
      { value: 0, label: '全部时长' },
      { value: 2, label: '2天1晚' },
      { value: 3, label: '3天2晚' },
      { value: 4, label: '4天3晚' },
      { value: 5, label: '5天4晚' },
      { value: 7, label: '7天+' }
    ],
    daysIndex: 0,

    trips: [],
    leftTrips: [],
    rightTrips: [],
    page: 1,
    hasMore: true,
    loadingMore: false,
    // 字母索引
    alphabetList: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split(''),
    alphabetScrollToId: ''
  },

  onLoad() {
    applyTheme(this);
    this.loadCityData();
    this.getCurrentLocation();
    // 启用分享
    share.enableShareMenu();
  },

  onShow() {
    applyTheme(this);
    this.loadHotTags();
    if (this._preserveScrollOnShow && this._hasLoaded) {
      this._preserveScrollOnShow = false;
      this.restoreScrollPosition();
      return;
    }
    // TabBar 切换或从其他页面返回时，已加载过数据则不重复请求
    if (this._hasLoaded && !app.globalData.squareDirty && this.data.trips.length > 0) {
      return;
    }
    app.globalData.squareDirty = false;
    this.loadData();
  },

  onPageScroll(e) {
    this._lastScrollTop = e.scrollTop || 0;
  },

  /** 根据 ID 生成稳定的占位封面 URL */
  _getPlaceholderCover(id, seed) {
    // H2: 防止 id 为 null/undefined 时生成无效 URL
    if (!id && !seed) return '/images/default-cover.png';
    const picId = (Math.abs(parseInt(String(id).replace(/\D/g, '')) || (seed || 0)) % 85) + 10;
    return 'https://picsum.photos/id/10' + picId + '/600/800';
  },

  /** 加载后端真实热门标签（缓存 30 分钟） */
  loadHotTags() {
    // 先展示缓存的标签
    const cachedTags = cache.get('hot_tags', 30 * 60 * 1000);
    if (cachedTags && cachedTags.length > 0) {
      this.setData({ hotTags: cachedTags });
    }
    api.getHotTags()
      .then((res) => {
        const list = this.normalizeListResponse(res);
        const hotTags = (list || [])
          .filter(item => item && item.name && item.name !== '轻松休闲')
          .slice(0, 8);
        this.setData({ hotTags });
        cache.set('hot_tags', hotTags);
      })
      .catch((err) => {
        console.error('加载热门标签失败:', err);
        if (!this.data.hotTags.length) {
          this.setData({ hotTags: [] });
        }
      });
  },


  /** 获取当前 GPS 定位城市 */
  getCurrentLocation() {
    const that = this;
    // L6: 先检查定位授权状态，再调用 wx.getLocation
    wx.getSetting({
      success(settingRes) {
        if (!settingRes.authSetting['scope.userLocation']) {
          // 未授权，请求授权
          wx.authorize({
            scope: 'scope.userLocation',
            success() { that._doGetLocation(); },
            fail() {
              console.warn('[square] 用户拒绝定位授权');
              that.setData({ currentLocation: { id: '', name: '授权失败' } });
            }
          });
        } else {
          that._doGetLocation();
        }
      },
      fail() { that._doGetLocation(); }
    });
  },

  /** 实际执行 GPS 定位 */
  _doGetLocation() {
    const that = this;
    wx.getLocation({
      type: 'gcj02',
      success(res) {
        const { longitude, latitude } = res;
        that.setData({ currentLocation: { id: '', name: '定位中...' } });

        // 通过后端 Haversine 算法匹配最近城市
        cityApi.getNearestCity(longitude, latitude)
          .then((city) => {
            if (city && city.name) {
              that.setData({
                currentLocation: { id: String(city.id), name: city.name }
              });
            } else {
              that.setData({ currentLocation: { id: '', name: 'GPS定位' } });
            }
          })
          .catch(() => {
            that.setData({ currentLocation: { id: '', name: 'GPS定位' } });
          });
      },
      fail(err) {
        console.warn('GPS定位失败:', err);
        that.setData({ currentLocation: { id: '', name: '定位失败' } });
      }
    });
  },

  /** 加载城市分组数据（缓存 24 小时） */
  loadCityData() {
    // 先展示缓存的城市数据
    const cachedCities = cache.get('city_data', 24 * 60 * 60 * 1000);
    if (cachedCities) {
      this.setData({ cityData: cachedCities });
    }
    cityApi.getGroupedCities()
      .then((res) => {
        this.setData({ cityData: res });
        cache.set('city_data', res);
      })
      .catch(() => {
        // 降级：从旧接口加载
        cityApi.getCities()
          .then((list) => {
            const grouped = this.buildFallbackCityData(list || []);
            this.setData({ cityData: grouped });
          })
          .catch(() => {
            this.setData({ cityData: null });
          });
      });
  },

  /** 降级方案：将扁平列表转为分组格式 */
  buildFallbackCityData(list) {
    const groups = {};
    const hotCities = [];
    list.forEach(city => {
      const letter = (city.pinyinShort || (city.pinyin || '')[0] || '').toUpperCase();
      if (!letter) return;
      if (!groups[letter]) groups[letter] = [];
      groups[letter].push({ id: String(city.id), name: city.name, pinyin: city.pinyin || '' });
      if (city.hot > 0) {
        hotCities.push({ id: String(city.id), name: city.name, pinyin: city.pinyin || '' });
      }
    });
    const sortedLetters = Object.keys(groups).sort();
    const groupCities = sortedLetters.map(letter => ({
      letter,
      cities: groups[letter].sort((a, b) => a.pinyin.localeCompare(b.pinyin))
    }));
    return {
      hotCities: hotCities.sort((a, b) => b.id - a.id).slice(0, 20),
      groupCities,
      totalCount: list.length
    };
  },

  /** 加载广场数据 - 首次或筛选变化时 */
  loadData(options = {}) {
    const preserveScroll = !!options.preserveScroll;
    if (!preserveScroll) {
      this._lastScrollTop = 0;
    }

    // 第一页先从缓存展示（30 秒内有效）
    const isFirstPage = this.data.page === 1;
    if (isFirstPage) {
      const cachedTrips = cache.get('index_trips_p1', 30 * 1000);
      if (cachedTrips && cachedTrips.length > 0) {
        const { leftTrips, rightTrips } = this.splitWaterfallData(cachedTrips);
        this.setData({
          trips: cachedTrips,
          leftTrips,
          rightTrips
        });
      }
    }

    this.setData({ loading: true, page: 1, hasMore: true });
    this.loadPublishedTrips().finally(() => {
      this.setData({ loading: false });
      this._hasLoaded = true;
      if (preserveScroll) {
        this.restoreScrollPosition();
      }
    });
  },

  restoreScrollPosition() {
    if (!this._lastScrollTop) return;
    wx.nextTick(() => {
      wx.pageScrollTo({
        scrollTop: this._lastScrollTop,
        duration: 0
      });
    });
  },

  /** 加载更多（下一页） */
  loadMore() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({ loadingMore: true, page: this.data.page + 1 });
    this.loadPublishedTrips().finally(() => {
      this.setData({ loadingMore: false });
    });
  },

  /** 兼容数组、分页对象和二次包裹对象 */
  normalizeListResponse(res) {
    if (Array.isArray(res)) return res;
    if (!res || typeof res !== 'object') return [];
    if (Array.isArray(res.records)) return res.records;
    if (Array.isArray(res.list)) return res.list;
    if (Array.isArray(res.rows)) return res.rows;
    if (Array.isArray(res.data)) return res.data;
    if (res.data && typeof res.data === 'object') return this.normalizeListResponse(res.data);
    return [];
  },

  /** 加载广场行程列表 */
  loadPublishedTrips() {
    const pg = this.data.page;
    // 多城市用逗号拼接城市名称
    const cityParam = this.data.selectedCityNames.join(',');
    const daysVal = this.data.daysOptions[this.data.daysIndex].value;

    const keyword = this.data.selectedTag || this.data.keyword;
    const category = this.data.currentCategory || 'recommend';

    return api.getPublishedTrips(category, keyword, pg, 10, cityParam, daysVal)
      .then((res) => {
        const list = this.normalizeListResponse(res);
        const newTrips = (list || []).map((item, idx) => {
          if (!item.coverImage) {
            item.coverImage = this._getPlaceholderCover(item.id, idx + 200);
          }
          return item;
        });
        const trips = pg === 1 ? newTrips : this.data.trips.concat(newTrips);
        const { leftTrips, rightTrips } = this.splitWaterfallData(trips);
        this.setData({
          trips,
          leftTrips,
          rightTrips,
          hasMore: newTrips.length >= 10
        });
        // 第一页写入缓存（默认视图，无筛选条件时优先缓存）
        if (pg === 1 && !cityParam && !keyword) {
          cache.set('index_trips_p1', trips);
        }
        return trips;
      })
      .catch((err) => {
        console.error('加载发现页行程失败:', err);
        if (err && err.message && err.message.includes('NOT_LOGIN')) {
          handleNotLogin(err);
        }
        if (pg === 1) {
          this.setData({ trips: [], leftTrips: [], rightTrips: [] });
        }
        return [];
      });
  },

  /** 将行程数据分为左右两列（瀑布流） */
  splitWaterfallData(trips) {
    const leftTrips = [];
    const rightTrips = [];
    let leftHeight = 0;
    let rightHeight = 0;

    trips.forEach((item) => {
      // H3: 基于图片 URL 的 hash 生成稳定高度，避免随机导致布局跳动
      // 如果有后端返回的 imageRatio 则使用真实宽高比
      let imgRatio = item.imageRatio || 0;
      if (!imgRatio && item.coverImage) {
        imgRatio = this._getStableImageRatio(item.coverImage);
      }
      // 卡片宽度约 345rpx，图片高度 = 宽度 / 宽高比，内容区约 180rpx
      const imgH = imgRatio > 0 ? Math.round(100 / imgRatio) : 75; // 无宽高比时默认 4:3
      const contentH = 50 + (item.title ? Math.min(item.title.length, 20) * 2.5 : 0)
        + (item.nickname ? 15 : 0) + 20; // 底部用户信息高度
      const estHeight = imgH + contentH;

      if (leftHeight <= rightHeight) {
        leftTrips.push(item);
        leftHeight += estHeight;
      } else {
        rightTrips.push(item);
        rightHeight += estHeight;
      }
    });

    return { leftTrips, rightTrips };
  },

  /**
   * H3: 基于图片 URL 生成稳定的宽高比（hash 算法）
   * 同一 URL 总是返回相同值，确保布局不跳动
   * @param {string} url 图片 URL
   * @returns {number} 宽高比（宽/高），范围 0.6 ~ 1.8
   */
  _getStableImageRatio(url) {
    let hash = 0;
    for (let i = 0; i < url.length; i++) {
      const ch = url.charCodeAt(i);
      hash = ((hash << 5) - hash) + ch;
      hash |= 0; // 转 32 位有符号整数
    }
    // 将 hash 映射到 0.6 ~ 1.8 范围（常见风景照宽高比）
    const normalized = (Math.abs(hash) % 1000) / 1000; // 0 ~ 0.999
    return 0.6 + normalized * 1.2;
  },

  /**
   * H3: 图片加载完成后，若有真实宽高比则微调（可选）
   * 在 WXML 中 bindload="onCoverLoad" 调用
   */
  onCoverLoad(e) {
    const { width, height } = e.detail;
    if (width && height) {
      // 可在此将真实宽高比保存，用于下次布局（需配合缓存）
      // 当前仅打印日志，供调试
    }
  },

  // ==================== 城市弹窗 ====================

  /** 打开城市选择弹窗 */
  openCityPopup() {
    this.setData({ showCityPopup: true });
  },

  /** 关闭城市选择弹窗 */
  closeCityPopup() {
    this.setData({ showCityPopup: false });
  },

  /** 阻止冒泡 */
  preventBubble() {},

  /** 切换城市选中状态（多选） */
  toggleCity(e) {
    const city = e.currentTarget.dataset.city;
    if (!city) return;

    const { selectedCities, selectedCityNames, selectedCityIds } = this.data;
    const idx = selectedCityIds.indexOf(city.id);

    let newSelected, newNames, newIds;
    if (idx >= 0) {
      // 取消选中
      newSelected = selectedCities.filter(c => c.id !== city.id);
      newNames = selectedCityNames.filter(n => n !== city.name);
      newIds = selectedCityIds.filter(id => id !== city.id);
    } else {
      // 添加选中
      newSelected = [...selectedCities, city];
      newNames = [...selectedCityNames, city.name];
      newIds = [...selectedCityIds, city.id];
    }

    this.setData({
      selectedCities: newSelected,
      selectedCityNames: newNames,
      selectedCityIds: newIds
    });
  },

  /** 判断城市是否已选中 */
  isCitySelected(cityId) {
    return this.data.selectedCities.some(c => c.id === cityId);
  },

  /** 点击当前定位城市 */
  selectCurrentLocation() {
    const loc = this.data.currentLocation;
    if (!loc || !loc.id) {
      wx.showToast({ title: '定位不可用', icon: 'none' });
      return;
    }
    // 如果已有城市ID，加入选中
    this.toggleCity({
      currentTarget: { dataset: { city: loc } }
    });
  },

  /** 字母索引点击 */
  onAlphabetTap(e) {
    const letter = e.currentTarget.dataset.letter;
    this.setData({
      alphabetScrollToId: 'letter-' + letter
    });
  },

  /** 确认城市选择 */
  confirmCitySelection() {
    this.setData({ showCityPopup: false, page: 1 });
    this.loadData();
  },

  /** 清除所有选中城市 */
  clearCitySelection() {
    this.setData({
      selectedCities: [],
      selectedCityNames: [],
      selectedCityIds: []
    });
  },

  /** 获取城市显示文本 */
  getCityDisplayText() {
    const names = this.data.selectedCityNames;
    if (names.length === 0) return '全部城市';
    if (names.length === 1) return names[0];
    return names[0] + ` 等${names.length}城`;
  },

  // ==================== 天数选择 ====================

  /** 天数选择器变化 */
  onDaysChange(e) {
    this.setData({ daysIndex: parseInt(e.detail.value), page: 1 });
    this.loadData();
  },

  // ==================== 分类与搜索 ====================

  /** 切换推荐/最新 */
  onCategoryTabTap(e) {
    const key = e.currentTarget.dataset.key;
    if (!key || key === this.data.currentCategory) return;
    this.setData({ currentCategory: key, page: 1 });
    this.loadData();
  },

  /** 切换热门标签筛选（再次点击取消） */
  onTagTap(e) {
    const tag = e.currentTarget.dataset.tag || '';
    const isDeselect = this.data.selectedTag === tag;
    const newTag = isDeselect ? '' : tag;
    this.setData({ selectedTag: newTag, keyword: newTag ? '' : this.data.keyword, page: 1 });
    this.loadData();
  },

  /** 展开/收起搜索框 */
  toggleSearchBar() {
    this.setData({ showSearchBar: !this.data.showSearchBar });
  },

  /** 清空搜索 */
  clearSearch() {
    const hadKeyword = !!this.data.keyword;
    const hadTag = !!this.data.selectedTag;
    this.setData({ keyword: '', selectedTag: '', showSearchBar: false, page: 1 });
    if (hadKeyword || hadTag) this.loadData();
  },

  /** 关键词输入 */
  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value });
  },

  /** 搜索 */
  onSearch() {
    this.setData({ selectedTag: '', page: 1 });
    this.loadData();
  },

  /** 发布行程 */
  publishTrip() {
    if (!auth.checkLogin()) return;
    wx.navigateTo({
      url: '/pages/chat/chat'
    });
  },

  /** 查看行程详情 */
  viewTripDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/trip-detail/trip-detail?publishId=${id}`,
      success: () => {
        this._preserveScrollOnShow = true;
      }
    });
  },

  /** 触底加载更多 */
  onReachBottom() {
    this.loadMore();
  },

  // ==================== 分享 ====================

  onShareAppMessage() {
    const keyword = this.data.keyword;
    const cities = this.data.selectedCityNames;
    let title = '拾路派 - 发现精彩旅途';
    if (cities.length > 0) {
      title = `拾路派 - ${cities.join('、')}旅行灵感`;
    } else if (keyword) {
      title = `拾路派 - 搜索"${keyword}"的旅行灵感`;
    }
    return share.shareToFriend({ title, path: '/pages/index/index' });
  },

  onShareTimeline() {
    const keyword = this.data.keyword;
    const cities = this.data.selectedCityNames;
    let title = '拾路派 - 发现精彩旅途';
    if (cities.length > 0) {
      title = `拾路派 - ${cities.join('、')}旅行灵感`;
    } else if (keyword) {
      title = `拾路派 - 搜索"${keyword}"的旅行灵感`;
    }
    return share.shareToTimeline({ title });
  }
});
