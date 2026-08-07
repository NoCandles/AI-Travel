const app = getApp();
const hotelData = require('../../utils/hotelData');
const { applyTheme } = require('../../utils/theme');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    // 参数
    tripId: '',
    dayIndex: 0,
    city: '',
    latitude: 0,
    longitude: 0,

    // 状态
    loading: true,
    error: '',
    hotels: [],
    pageIndex: 1,
    hasMore: true,
    selectedHotelId: ''
  },

  onLoad(options) {
    applyTheme(this);

    const tripId = options.tripId || '';
    const dayIndex = parseInt(options.dayIndex || 0);
    const city = decodeURIComponent(options.city || '');
    const latitude = parseFloat(options.latitude || 0);
    const longitude = parseFloat(options.longitude || 0);

    this.setData({
      tripId,
      dayIndex,
      city,
      latitude,
      longitude
    });

    // 如果前一个页面已选了酒店，标记选中状态
    if (options.selectedHotelId) {
      this.setData({ selectedHotelId: options.selectedHotelId });
    }

    this.searchHotels(true);
  },

  onShow() {
    applyTheme(this);
  },

  /** 搜索附近酒店 */
  searchHotels(refresh = false) {
    const { latitude, longitude, city } = this.data;
    const pageIndex = refresh ? 1 : this.data.pageIndex;

    if (!latitude && !longitude && !city) {
      this.setData({
        loading: false,
        error: '缺少位置信息，无法搜索附近酒店'
      });
      return;
    }

    this.setData({ loading: true, error: '' });

    hotelData.searchHotels(latitude, longitude, city, 3000)
      .then((hotels) => {
        const list = (hotels || []).map((item) => ({
          id: item.id,
          title: item.name,
          address: item.address || '',
          tel: item.tel || '',
          latitude: item.latitude,
          longitude: item.longitude,
          distance: item.distance || 0,
          distanceText: item.distanceText || '',
          category: item.category || '',
          tags: item.tags || [],
          decisionHint: item.distanceText
            ? `距当日行程区域 ${item.distanceText}`
            : (item.category ? `${item.category}，可先在地图确认通勤位置` : '可先在地图确认通勤位置')
        }));

        this.setData({
          hotels: refresh ? list : [...this.data.hotels, ...list],
          loading: false,
          pageIndex: pageIndex,
          hasMore: list.length >= 20
        });
      })
      .catch((err) => {
        console.error('搜索酒店失败:', err);
        this.setData({
          loading: false,
          error: '搜索酒店失败，请稍后重试'
        });
      });
  },

  /** 加载更多 */
  loadMore() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({ pageIndex: this.data.pageIndex + 1 });
    this.searchHotels(false);
  },

  /** 查看酒店地图位置 */
  viewOnMap(e) {
    const hotel = e.currentTarget.dataset.hotel;
    if (!hotel || !hotel.latitude || !hotel.longitude) {
      wx.showToast({ title: '暂无位置信息', icon: 'none' });
      return;
    }
    wx.openLocation({
      latitude: hotel.latitude,
      longitude: hotel.longitude,
      name: hotel.title,
      address: hotel.address,
      scale: 16
    });
  },

  /** 拨打电话 */
  callHotel(e) {
    const tel = e.currentTarget.dataset.tel;
    if (!tel) {
      wx.showToast({ title: '暂无联系电话', icon: 'none' });
      return;
    }
    wx.makePhoneCall({
      phoneNumber: tel
    });
  },

  /** 选择此酒店 */
  selectHotel(e) {
    const hotel = e.currentTarget.dataset.hotel;
    if (!hotel) return;

    // 保存到持久存储，供前一个页面在 onShow 中读取（替代 app.globalData，避免被回收）
    wx.setStorageSync('selectedHotel', {
      ...hotel,
      dayIndex: this.data.dayIndex
    });

    wx.navigateBack();
  },

  /** 重试 */
  retry() {
    this.searchHotels(true);
  }
});
