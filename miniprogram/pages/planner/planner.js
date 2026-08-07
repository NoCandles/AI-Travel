const app = getApp();
const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');
const analytics = require('../../utils/analytics');

Page({
  data: {
    tripId: '',
    isEdit: false,
    expandedDay: -1,
    trip: null,
    loading: true,
    darkMode: wx.getStorageSync('darkMode') || false,
    name: '',
    destination: '',
    startDate: '',
    endDate: '',
    budgetIndex: 0,
    budgetOptions: ['经济型', '中端型', '高端型'],
    customBudget: '',              // 自定义总预算（元），填了则覆盖档位
    showAdvancedOptions: false,
    routeChangeNotice: '',
    daysCount: 3,
    description: '',
    // 新增功能状态
    calendarSynced: false,
    versions: [],
    showVersionPanel: false,
    studioTab: 'route',
    submittingPublish: false,
    publishId: '',
    publishDescription: '',
    publishContent: '',
    publishCoverImage: '',
    publishLocation: '',
    publishDays: 1,
    publishNights: 1,
    publishTags: [],
    publishTagInput: '',
    hotTags: ['亲子', '美食', '小众', '周末', '自驾', '徒步', '避暑', '摄影'],
    candidateImages: [],
    selectedImages: [],
    maxImages: 9,
    // 景点图片 - 素材选择弹窗
    showMaterialPicker: false,
    materialPickerDay: -1,
    materialPickerSpot: -1
  },

  onLoad(options) {
    applyTheme(this);
    const studioTab = options.tab || 'route';
    if (options.id) {
      this.setData({ tripId: options.id, isEdit: true, studioTab });
      this.loadTripDetail(options.id);
    } else if (options.tripPlanId) {
      this.setData({ tripId: options.tripPlanId, isEdit: true, studioTab });
      this.loadTripDetail(options.tripPlanId);
    } else if (options.publishId) {
      this.setData({ publishId: options.publishId, isEdit: true, studioTab: 'publish' });
      this.loadPublishForStudio(options.publishId);
    } else {
      this.setData({ loading: false, studioTab });
      wx.setNavigationBarTitle({ title: '新建行程' });
    }
    // 启用分享
    share.enableShareMenu();
  },

  onShow() {
    applyTheme(this);

    // 检查是否有从酒店列表返回的已选酒店（从持久存储读取）
    const selectedHotel = wx.getStorageSync('selectedHotel');
    if (selectedHotel && selectedHotel.dayIndex !== undefined && this.data.trip && this.data.trip.days) {
      const dayIdx = selectedHotel.dayIndex;
      const day = this.data.trip.days[dayIdx];
      if (day) {
        const path = `trip.days[${dayIdx}]`;
        this.setData({
          [path + '.hotelName']: selectedHotel.title || selectedHotel.name || '',
          [path + '.hotelDetail']: selectedHotel.address || '',
          [path + '.hotelPrice']: ''
        });
      }
      wx.removeStorageSync('selectedHotel');
    }
  },

  // ==================== 加载行程详情 ====================

  loadTripDetail(id) {
    api.getTripDetail(id)
      .then((trip) => {
        const budgetMap = {
          'economy': 0, '经济': 0, '经济型': 0,
          'medium': 1, '中端': 1, '中端型': 1,
          'luxury': 2, '高端': 2, '高端型': 2
        };
        // 解析预算：优先检测自定义格式（如 "总预算2000元"）
        let budgetIdx = 1;
        let customBudget = '';
        if (trip.budget) {
          if (budgetMap[trip.budget] !== undefined) {
            budgetIdx = budgetMap[trip.budget];
          } else {
            // 尝试从字符串中提取金额数字
            const numMatch = trip.budget.match(/(\d+)/);
            if (numMatch) {
              customBudget = numMatch[1];
            }
            // 根据关键字估算档位（仅用于UI显示）
            if (trip.budget.includes('经济') || trip.budget.includes('economy')) budgetIdx = 0;
            else if (trip.budget.includes('高端') || trip.budget.includes('luxury')) budgetIdx = 2;
            else budgetIdx = 1;
          }
        }

        // 格式化日期和天数数据（展开 hotel 嵌套为扁平字段方便编辑）
        if (trip.startDate) trip.startDate = trip.startDate.split(' ')[0];
        if (trip.endDate) trip.endDate = trip.endDate.split(' ')[0];
        if (trip.days) {
          trip.days = trip.days.map(d => {
            const day = {
              ...d,
              _expanded: false,
              date: d.date ? d.date.split(' ')[0] : d.date,
              hotelName: '',
              hotelDetail: '',
              hotelPrice: '',
              spots: (d.spots || []).map(s => ({ ...s, _spotExpanded: false }))
            };
            if (d.hotel) {
              day.hotelName = d.hotel.name || '';
              day.hotelDetail = d.hotel.detail || '';
              day.hotelPrice = d.hotel.price || '';
            }
            return day;
          });
        }

        this.setData({
          trip: trip,
          name: trip.name || '',
          destination: trip.destination || '',
          startDate: trip.startDate || '',
          endDate: trip.endDate || '',
          budgetIndex: budgetIdx,
          customBudget: customBudget,
          daysCount: trip.days ? trip.days.length : 3,
          description: trip.description || '',
          loading: false
        });
        wx.setNavigationBarTitle({ title: '行程工作台' });
        this.loadPublishDraftForStudio(id, trip);
      })
      .catch(() => {
        this.setData({ loading: false });
      });
  },

  // ==================== 基本信息编辑 ====================

  onNameInput(e) {
    this.setData({ name: e.detail.value });
  },

  onDestinationInput(e) {
    this.setData({ destination: e.detail.value });
  },

  onStartDateChange(e) {
    const start = e.detail.value;
    this.setData({ startDate: start });
    this.updateDaysCount(start, this.data.endDate);
  },

  onEndDateChange(e) {
    const end = e.detail.value;
    this.setData({ endDate: end });
    this.updateDaysCount(this.data.startDate, end);
  },

  updateDaysCount(start, end) {
    if (start && end) {
      const s = new Date(start.replace(/-/g, '/'));
      const e = new Date(end.replace(/-/g, '/'));
      const days = Math.ceil((e - s) / (1000 * 60 * 60 * 24)) + 1;
      if (days > 0) this.setData({ daysCount: days });
    }
  },

  onBudgetPick(e) {
    this.setData({ budgetIndex: parseInt(e.detail.value), customBudget: '' });
  },

  onCustomBudgetInput(e) {
    const val = e.detail.value.replace(/[^\d]/g, '');
    this.setData({ customBudget: val });
  },

  clearCustomBudget() {
    this.setData({ customBudget: '' });
  },

  toggleAdvancedOptions() {
    this.setData({ showAdvancedOptions: !this.data.showAdvancedOptions });
  },

  onDescriptionInput(e) {
    this.setData({ description: e.detail.value });
  },

  switchStudioTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (!tab) return;
    if (tab !== 'route' && (!this.data.isEdit || !this.data.tripId)) {
      wx.showToast({ title: '请先创建行程', icon: 'none' });
      return;
    }
    this.setData({ studioTab: tab });
    if ((tab === 'assets' || tab === 'publish') && this.data.tripId && !this.data.candidateImages.length) {
      this.loadPublishDraftForStudio(this.data.tripId, this.data.trip);
    }
  },

  // ==================== 路线编辑 ====================

  // 展开/折叠某一天的编辑区
  toggleEditDay(e) {
    var idx = e.currentTarget.dataset.index;
    if (idx === undefined || idx === null) return;
    idx = Number(idx);
    if (isNaN(idx)) return;
    var newVal = this.data.expandedDay === idx ? -1 : idx;
    this.setData({ expandedDay: newVal });
  },

  // 编辑某一天的天气/温度/备注
  onDayFieldChange(e) {
    const { day, field } = e.currentTarget.dataset;
    const value = e.detail.value;
    const path = `trip.days[${day}].${field}`;
    this.setData({ [path]: value });
  },

  // 展开/折叠某个景点的编辑区
  toggleEditSpot(e) {
    const { day, spot } = e.currentTarget.dataset;
    const days = [...this.data.trip.days];
    const spots = days[day].spots.map((s, i) => ({
      ...s,
      _spotExpanded: i === spot ? !s._spotExpanded : false
    }));
    days[day].spots = spots;
    this.setData({ 'trip.days': days });
  },

  // 编辑某个景点的字段（实时更新本地数据，不立即保存）
  onSpotFieldChange(e) {
    const { day, spot, field } = e.currentTarget.dataset;
    const value = e.detail.value;
    const path = `trip.days[${day}].spots[${spot}].${field}`;
    this.setData({ [path]: value });
  },

  // 保存单个景点修改
  saveSpotChanges(e) {
    const { day, spot } = e.currentTarget.dataset;
    const spotData = this.data.trip.days[day].spots[spot];

    if (!spotData.id) {
      wx.showToast({ title: '景点数据异常', icon: 'none' });
      return;
    }

    api.updateSpot(spotData.id, {
      name: spotData.name,
      arrivalTime: spotData.arrivalTime,
      duration: spotData.duration,
      address: spotData.address,
      cost: spotData.cost,
      tips: spotData.tips,
      travelGuide: spotData.travelGuide || '',
      image: spotData.image || ''
    }).then(() => {
      // 保存后自动折叠
      const days = [...this.data.trip.days];
      days[day].spots[spot]._spotExpanded = false;
      this.setData({ 'trip.days': days });
    }).catch(() => {
      wx.showToast({ title: '保存失败', icon: 'none' });
    });
  },

  uploadSpotImage(e) {
    const dayIdx = Number(e.currentTarget.dataset.day);
    const spotIdx = Number(e.currentTarget.dataset.spot);
    const spot = this.data.trip.days[dayIdx].spots[spotIdx];
    if (!spot || !spot.id) {
      wx.showToast({ title: '请先保存景点', icon: 'none' });
      return;
    }

    const hasMaterials = this.data.candidateImages && this.data.candidateImages.length > 0;
    const itemList = hasMaterials
      ? ['从图片素材选择', '从相册选择', '拍照']
      : ['从相册选择', '拍照'];

    wx.showActionSheet({
      itemList,
      success: (res) => {
        const idx = res.tapIndex;
        if (hasMaterials) {
          if (idx === 0) {
            // 从素材库选择
            this.setData({
              showMaterialPicker: true,
              materialPickerDay: dayIdx,
              materialPickerSpot: spotIdx
            });
            return;
          }
          if (idx === 1) {
            // 从相册选择
            this.chooseSpotImageFromAlbum(dayIdx, spotIdx, spot);
            return;
          }
          // 拍照
          this.chooseSpotImageFromCamera(dayIdx, spotIdx, spot);
        } else {
          if (idx === 0) {
            this.chooseSpotImageFromAlbum(dayIdx, spotIdx, spot);
          } else {
            this.chooseSpotImageFromCamera(dayIdx, spotIdx, spot);
          }
        }
      }
    });
  },

  chooseSpotImageFromAlbum(dayIdx, spotIdx, spot) {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album'],
      success: (res) => {
        const file = res.tempFiles && res.tempFiles[0];
        if (!file || !file.tempFilePath) return;
        this.uploadSpotImageFile(dayIdx, spotIdx, spot, file.tempFilePath);
      }
    });
  },

  chooseSpotImageFromCamera(dayIdx, spotIdx, spot) {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      success: (res) => {
        const file = res.tempFiles && res.tempFiles[0];
        if (!file || !file.tempFilePath) return;
        this.uploadSpotImageFile(dayIdx, spotIdx, spot, file.tempFilePath);
      }
    });
  },

  closeMaterialPicker() {
    this.setData({ showMaterialPicker: false, materialPickerDay: -1, materialPickerSpot: -1 });
  },

  selectMaterialForSpot(e) {
    const imageUrl = e.currentTarget.dataset.url;
    if (!imageUrl) return;
    const dayIdx = this.data.materialPickerDay;
    const spotIdx = this.data.materialPickerSpot;
    const spot = this.data.trip.days[dayIdx].spots[spotIdx];
    if (!spot || !spot.id) return;

    this.setData({ showMaterialPicker: false });

    api.updateSpot(spot.id, {
      name: spot.name,
      arrivalTime: spot.arrivalTime,
      duration: spot.duration,
      address: spot.address,
      cost: spot.cost,
      tips: spot.tips,
      travelGuide: spot.travelGuide || '',
      image: imageUrl
    }).then(() => {
      this.setData({
        [`trip.days[${dayIdx}].spots[${spotIdx}].image`]: imageUrl,
        [`trip.days[${dayIdx}].spots[${spotIdx}].coverImage`]: imageUrl,
        materialPickerDay: -1,
        materialPickerSpot: -1
      });
      if (!this.data.trip.coverImage) {
        this.setTripCoverImage(imageUrl);
      }
    }).catch(() => {
      wx.showToast({ title: '设置失败', icon: 'none' });
    });
  },

  uploadSpotImageFile(dayIdx, spotIdx, spot, tempFilePath) {
    const userId = wx.getStorageSync('userId') || 'user';
    const suffix = (tempFilePath.split('.').pop() || 'jpg').toLowerCase();
    const cloudPath = `trip-spots/${userId}/${spot.id}_${Date.now()}.${suffix}`;
    wx.cloud.uploadFile({
      cloudPath,
      filePath: tempFilePath
    }).then((res) => {
      const imageUrl = res.fileID;
      return api.updateSpot(spot.id, {
        name: spot.name,
        arrivalTime: spot.arrivalTime,
        duration: spot.duration,
        address: spot.address,
        cost: spot.cost,
        tips: spot.tips,
        travelGuide: spot.travelGuide || '',
        image: imageUrl
      }).then(() => imageUrl);
    }).then((imageUrl) => {
      this.setData({
        [`trip.days[${dayIdx}].spots[${spotIdx}].image`]: imageUrl,
        [`trip.days[${dayIdx}].spots[${spotIdx}].coverImage`]: imageUrl
      });
      if (!this.data.trip.coverImage) {
        this.setTripCoverImage(imageUrl);
      }
    }).catch(() => {
      wx.showToast({ title: '上传失败', icon: 'none' });
    });
  },

  setSpotAsCover(e) {
    const dayIdx = Number(e.currentTarget.dataset.day);
    const spotIdx = Number(e.currentTarget.dataset.spot);
    const spot = this.data.trip.days[dayIdx].spots[spotIdx];
    const imageUrl = spot && (spot.image || spot.coverImage);
    if (!imageUrl) {
      wx.showToast({ title: '请先上传景点图', icon: 'none' });
      return;
    }
    this.setTripCoverImage(imageUrl);
  },

  setTripCoverImage(imageUrl) {
    if (!this.data.tripId) return;
    api.updateTrip(this.data.tripId, { coverImage: imageUrl })
      .then(() => {
        this.setData({ 'trip.coverImage': imageUrl });
      })
      .catch(() => {
        wx.showToast({ title: '封面同步失败', icon: 'none' });
      });
  },

  // 确认删除景点
  confirmDeleteSpot(e) {
    const { day, spot } = e.currentTarget.dataset;
    const dayIdx = parseInt(day);
    const spotIdx = parseInt(spot);
    const spotData = this.data.trip.days[dayIdx].spots[spotIdx];

    if (!spotData || !spotData.id) {
      wx.showToast({ title: '景点数据异常', icon: 'none' });
      return;
    }

    wx.showModal({
      title: '删除景点',
      content: '确定要删除「' + (spotData.name || '未命名') + '」吗？',
      success: (res) => {
        if (res.confirm) {
          this.deleteSpot(dayIdx, spotIdx, spotData.id);
        }
      }
    });
  },

  deleteSpot(day, spot, spotId) {
    api.deleteSpot(spotId)
      .then(() => {
        const days = [...this.data.trip.days];
        days[day].spots.splice(spot, 1);
        this.setData({ 'trip.days': days });
      })
      .catch((err) => {
        console.error('删除景点失败:', err, 'spotId:', spotId);
        wx.showToast({ title: '删除失败，请重试', icon: 'none' });
      });
  },

  // 显示添加景点的输入框
  showAddSpotInput(e) {
    const day = e.currentTarget.dataset.day;
    const days = [...this.data.trip.days];
    days[day]._addingSpot = true;
    days[day]._newSpotName = '';
    this.setData({ 'trip.days': days });
  },

  // 取消添加
  cancelAddSpot(e) {
    const day = e.currentTarget.dataset.day;
    const days = [...this.data.trip.days];
    days[day]._addingSpot = false;
    this.setData({ 'trip.days': days });
  },

  // 输入景点名称
  onNewSpotNameInput(e) {
    const day = e.currentTarget.dataset.day;
    const path = `trip.days[${day}]._newSpotName`;
    this.setData({ [path]: e.detail.value });
  },

  // 确认添加景点
  confirmAddSpot(e) {
    const day = e.currentTarget.dataset.day;
    const dayData = this.data.trip.days[day];
    const name = (dayData._newSpotName || '').trim();

    if (!name) {
      wx.showToast({ title: '请输入景点名称', icon: 'none' });
      return;
    }

    // 隐藏输入框
    const days = [...this.data.trip.days];
    days[day]._addingSpot = false;
    this.setData({ 'trip.days': days });

    api.addSpot(this.data.tripId, dayData.id, {
      name: name,
      arrivalTime: '',
      duration: '',
      cost: '',
      tips: ''
    }).then((newSpot) => {
      const updatedDays = [...this.data.trip.days];
      if (!updatedDays[day].spots) updatedDays[day].spots = [];
      updatedDays[day].spots.push({
        ...newSpot,
        _spotExpanded: false,
        arrivalTime: newSpot.arrivalTime || '',
        duration: newSpot.duration || '',
        address: newSpot.address || '',
        cost: newSpot.cost || '',
        tips: newSpot.tips || ''
      });
      this.setData({ 'trip.days': updatedDays });
    }).catch(() => {
      wx.showToast({ title: '添加失败', icon: 'none' });
    });
  },

  /** 选择酒店 */
  selectHotel(e) {
    const dayIdx = parseInt(e.currentTarget.dataset.day);
    const city = this.data.destination || '';

    app.globalData.pendingHotelSearch = {
      city: city,
      latitude: 0,
      longitude: 0,
      keyword: '',
      dayIndex: dayIdx,
      source: 'planner'
    };

    wx.navigateTo({
      url: '/pages/hotel-list/hotel-list?city=' + encodeURIComponent(city) + '&dayIndex=' + dayIdx
    });
  },

  // 保存当天信息（天气、酒店）
  saveDayChanges(e) {
    const { day } = e.currentTarget.dataset;
    const dayData = this.data.trip.days[day];

    api.updateDay(dayData.id, {
      weather: dayData.weather || '',
      temperature: dayData.temperature || '',
      hotel: {
        name: dayData.hotelName || '',
        detail: dayData.hotelDetail || '',
        price: dayData.hotelPrice ? parseInt(dayData.hotelPrice) : 0
      }
    }).then(() => {
      // 静默保存
    }).catch(() => {
      wx.showToast({ title: '保存失败', icon: 'none' });
    });
  },

  // ==================== 保存基本信息 ====================

  saveTrip() {
    if (!auth.checkLogin()) return;

    if (!this.data.destination) {
      wx.showToast({ title: '请输入目的地', icon: 'none' });
      return;
    }

    const tripData = {
      name: this.data.name || (this.data.destination + '之旅'),
      destination: this.data.destination,
      startDate: this.data.startDate,
      endDate: this.data.endDate,
      budget: this.data.customBudget ? `总预算${this.data.customBudget}元` : this.data.budgetOptions[this.data.budgetIndex],
      description: this.data.description
    };

    if (this.data.isEdit) {
      api.updateTrip(this.data.tripId, tripData)
        .then(() => {
          app.globalData.tripListDirty = true;
          app.globalData.squareDirty = true;
          app.globalData.statsDirty = true;
          wx.navigateBack();
        })
        .catch(() => {});
    } else {
      api.createTrip(tripData)
        .then(() => {
          app.globalData.tripListDirty = true;
          app.globalData.squareDirty = true;
          wx.navigateBack();
        })
        .catch(() => {});
    }
  },

  getTripCoverImage(trip) {
    if (!trip) return '';
    if (trip.coverImage) return trip.coverImage;
    const days = trip.days || [];
    for (let i = 0; i < days.length; i++) {
      const spots = days[i].spots || [];
      for (let j = 0; j < spots.length; j++) {
        const spot = spots[j] || {};
        if (spot.image) return spot.image;
        if (spot.coverImage) return spot.coverImage;
      }
    }
    return '';
  },

  // ==================== 编辑页 - 景点排序 ====================

  moveSpotUp(e) {
    const dayIdx = parseInt(e.currentTarget.dataset.day);
    const spotIdx = parseInt(e.currentTarget.dataset.spot);
    const spots = [...(this.data.trip.days[dayIdx].spots || [])];
    const previousSpots = [...spots];
    if (spotIdx <= 0) return;

    [spots[spotIdx], spots[spotIdx - 1]] = [spots[spotIdx - 1], spots[spotIdx]];
    this.setData({
      [`trip.days[${dayIdx}].spots`]: spots,
      routeChangeNotice: '已调整顺序，正在按新路线更新…'
    });
    analytics.track('route_reorder_preview_shown', { tripId: this.data.tripId, dayIndex: dayIdx, direction: 'up' });

    const spotIds = spots.map(s => s.id).filter(Boolean);
    const dayId = this.data.trip.days[dayIdx].id;
    if (dayId && spotIds.length > 1) {
      api.reorderSpots(dayId, spotIds)
        .then(() => this.setData({ routeChangeNotice: '路线顺序已保存，可在地图页查看更新。' }))
        .catch(() => this.setData({
          [`trip.days[${dayIdx}].spots`]: previousSpots,
          routeChangeNotice: '路线顺序未保存，已恢复原顺序，请稍后重试。'
        }));
    }
  },

  moveSpotDown(e) {
    const dayIdx = parseInt(e.currentTarget.dataset.day);
    const spotIdx = parseInt(e.currentTarget.dataset.spot);
    const spots = [...(this.data.trip.days[dayIdx].spots || [])];
    const previousSpots = [...spots];
    if (spotIdx >= spots.length - 1) return;

    [spots[spotIdx], spots[spotIdx + 1]] = [spots[spotIdx + 1], spots[spotIdx]];
    this.setData({
      [`trip.days[${dayIdx}].spots`]: spots,
      routeChangeNotice: '已调整顺序，正在按新路线更新…'
    });
    analytics.track('route_reorder_preview_shown', { tripId: this.data.tripId, dayIndex: dayIdx, direction: 'down' });

    const spotIds = spots.map(s => s.id).filter(Boolean);
    const dayId = this.data.trip.days[dayIdx].id;
    if (dayId && spotIds.length > 1) {
      api.reorderSpots(dayId, spotIds)
        .then(() => this.setData({ routeChangeNotice: '路线顺序已保存，可在地图页查看更新。' }))
        .catch(() => this.setData({
          [`trip.days[${dayIdx}].spots`]: previousSpots,
          routeChangeNotice: '路线顺序未保存，已恢复原顺序，请稍后重试。'
        }));
    }
  },

  // ==================== 图片素材 / 发布设置 ====================

  loadPublishForStudio(publishId) {
    const userId = wx.getStorageSync('userId') || '';
    api.getPublishDetail(publishId, userId)
      .then((data) => {
        const tripPlanId = data && data.tripPlanId;
        if (!tripPlanId) {
          this.hydratePublishStudio(data || {});
          this.setData({ loading: false });
          return;
        }
        this.setData({ tripId: tripPlanId });
        return api.getTripDetail(tripPlanId).then((trip) => {
          this.setData({ isEdit: true });
          this.hydrateTripFromDetail(trip);
          this.hydratePublishStudio(data || {});
        });
      })
      .catch(() => {
        this.setData({ loading: false });
        wx.showToast({ title: '发布加载失败', icon: 'none' });
      });
  },

  hydrateTripFromDetail(trip) {
    if (!trip) return;
    if (trip.startDate) trip.startDate = trip.startDate.split(' ')[0];
    if (trip.endDate) trip.endDate = trip.endDate.split(' ')[0];
    if (trip.days) {
      trip.days = trip.days.map(d => ({
        ...d,
        _expanded: false,
        date: d.date ? d.date.split(' ')[0] : d.date,
        hotelName: d.hotel ? (d.hotel.name || '') : '',
        hotelDetail: d.hotel ? (d.hotel.detail || '') : '',
        hotelPrice: d.hotel ? (d.hotel.price || '') : '',
        spots: (d.spots || []).map(s => ({ ...s, _spotExpanded: false }))
      }));
    }
    this.setData({
      trip,
      name: trip.name || '',
      destination: trip.destination || '',
      startDate: trip.startDate || '',
      endDate: trip.endDate || '',
      daysCount: trip.days ? trip.days.length : 1,
      description: trip.description || '',
      loading: false
    });
  },

  loadPublishDraftForStudio(tripPlanId, trip) {
    if (!tripPlanId) return;
    api.getPublishDraft(tripPlanId)
      .then((data) => this.hydratePublishStudio(data || {}, trip))
      .catch(() => {});
  },

  hydratePublishStudio(data, trip) {
    const candidateImages = this.buildStudioCandidateImages(data, trip || this.data.trip);
    const selectedImages = (data.images && data.images.length ? data.images : candidateImages.slice(0, this.data.maxImages))
      .map((item, index) => this.normalizeStudioImage(item, index))
      .filter(item => item.imageUrl);
    const coverImage = data.coverImage || this.getFirstStudioImageUrl(selectedImages) || '';
    const dayCount = data.days || (trip && trip.days ? trip.days.length : this.data.daysCount || 1);

    this.setData({
      publishId: data.id || this.data.publishId,
      publishDescription: data.description || '',
      publishContent: data.content || '',
      publishCoverImage: coverImage,
      publishLocation: data.location || this.data.destination || '',
      publishDays: dayCount,
      publishNights: data.nights || Math.max(1, dayCount - 1),
      publishTags: Array.isArray(data.tags) ? data.tags : [],
      candidateImages: this.markStudioCandidateImages(candidateImages, selectedImages),
      selectedImages: this.ensureStudioCoverFirst(selectedImages, coverImage)
    });
  },

  buildStudioCandidateImages(data, trip) {
    const result = [];
    const seen = {};
    const pushImage = (image) => {
      const item = this.normalizeStudioImage(image, result.length);
      if (!item.imageUrl || seen[item.imageUrl]) return;
      seen[item.imageUrl] = true;
      result.push(item);
    };

    pushImage({ imageUrl: data.coverImage || (trip && trip.coverImage), imageType: 'cover', mediaType: 'image', sourceType: 'trip_cover' });
    (data.images || []).forEach(pushImage);
    ((data.dayList || [])).forEach(day => (day.spots || []).forEach(spot => {
      pushImage({ imageUrl: spot.image || spot.coverImage, imageType: 'gallery', mediaType: 'image', sourceType: 'spot_image', sourceId: spot.id });
    }));
    ((trip && trip.days) || []).forEach(day => (day.spots || []).forEach(spot => {
      pushImage({ imageUrl: spot.image || spot.coverImage, imageType: 'gallery', mediaType: 'image', sourceType: 'spot_image', sourceId: spot.id });
    }));

    return result;
  },

  normalizeStudioImage(image, index) {
    const imageUrl = image && (image.imageUrl || image.url || image.fileID || image.tempFilePath || image.path);
    const mediaType = image && (image.mediaType || image.type || image.fileType) || 'image';
    return {
      id: image && image.id,
      imageUrl: imageUrl || '',
      imageType: image && image.imageType || 'gallery',
      mediaType: mediaType === 'video' ? 'video' : 'image',
      sourceType: image && image.sourceType || 'upload',
      sourceId: image && image.sourceId || '',
      sortOrder: image && image.sortOrder != null ? image.sortOrder : index
    };
  },

  ensureStudioCoverFirst(images, coverImage) {
    const list = images.slice();
    const coverIndex = list.findIndex(item => item.imageUrl === coverImage);
    if (coverIndex > 0) {
      const cover = list.splice(coverIndex, 1)[0];
      list.unshift(cover);
    }
    return list.map((item, index) => ({
      ...item,
      imageType: item.imageUrl === coverImage ? 'cover' : 'gallery',
      sortOrder: index
    }));
  },

  getFirstStudioImageUrl(images) {
    const image = (images || []).find(item => (item.mediaType || 'image') !== 'video' && item.imageUrl);
    return image ? image.imageUrl : '';
  },

  markStudioCandidateImages(candidateImages, selectedImages) {
    const selectedMap = {};
    selectedImages.forEach(item => {
      if (item.imageUrl) selectedMap[item.imageUrl] = true;
    });
    return candidateImages.map(item => ({ ...item, selected: !!selectedMap[item.imageUrl] }));
  },

  chooseStudioCover(e) {
    const imageUrl = e.currentTarget.dataset.url;
    this.setData({
      publishCoverImage: imageUrl,
      selectedImages: this.ensureStudioCoverFirst(this.data.selectedImages, imageUrl)
    });
  },

  toggleStudioImage(e) {
    const imageUrl = e.currentTarget.dataset.url;
    const image = this.data.candidateImages.find(item => item.imageUrl === imageUrl);
    if (!image) return;
    const selectedImages = this.data.selectedImages.slice();
    const index = selectedImages.findIndex(item => item.imageUrl === imageUrl);
    if (index >= 0) {
      selectedImages.splice(index, 1);
    } else {
      if (selectedImages.length >= this.data.maxImages) {
        wx.showToast({ title: '最多选择9张图片', icon: 'none' });
        return;
      }
      selectedImages.push(image);
    }
    const coverImage = selectedImages.find(item => item.imageUrl === this.data.publishCoverImage && item.mediaType !== 'video')
      ? this.data.publishCoverImage
      : this.getFirstStudioImageUrl(selectedImages);
    this.setData({
      publishCoverImage: coverImage,
      selectedImages: this.ensureStudioCoverFirst(selectedImages, coverImage),
      candidateImages: this.markStudioCandidateImages(this.data.candidateImages, selectedImages)
    });
  },

  removeStudioSelectedImage(e) {
    const imageUrl = e.currentTarget.dataset.url;
    const selectedImages = this.data.selectedImages.filter(item => item.imageUrl !== imageUrl);
    const coverImage = imageUrl === this.data.publishCoverImage ? this.getFirstStudioImageUrl(selectedImages) : this.data.publishCoverImage;
    this.setData({
      publishCoverImage: coverImage,
      selectedImages: this.ensureStudioCoverFirst(selectedImages, coverImage),
      candidateImages: this.markStudioCandidateImages(this.data.candidateImages, selectedImages)
    });
  },

  uploadStudioImages() {
    wx.chooseMedia({
      count: Math.max(1, this.data.maxImages - this.data.selectedImages.length),
      mediaType: ['image', 'video'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const files = res.tempFiles || [];
        if (!files.length) return;
        this.uploadStudioChosenFiles(files);
      }
    });
  },

  uploadStudioChosenFiles(files) {
    const userId = wx.getStorageSync('userId') || 'user';
    const tasks = files.map((file, index) => {
      const tempFilePath = file.tempFilePath;
      const mediaType = file.fileType || file.type || (tempFilePath.toLowerCase().includes('.mp4') ? 'video' : 'image');
      const suffix = (tempFilePath.split('.').pop() || (mediaType === 'video' ? 'mp4' : 'jpg')).toLowerCase();
      const cloudPath = `publish/${userId}/${Date.now()}_${index}.${suffix}`;
      return wx.cloud.uploadFile({ cloudPath, filePath: tempFilePath }).then(uploadRes => ({
        ...uploadRes,
        mediaType: mediaType === 'video' ? 'video' : 'image'
      }));
    });

    Promise.all(tasks)
      .then((results) => {
        const newImages = results.map((item, index) => this.normalizeStudioImage({
          imageUrl: item.fileID,
          imageType: 'gallery',
          mediaType: item.mediaType,
          sourceType: 'upload',
          sortOrder: this.data.selectedImages.length + index
        }, index));
        const selectedImages = this.data.selectedImages.concat(newImages).slice(0, this.data.maxImages);
        const candidateImages = this.data.candidateImages.concat(newImages);
        const coverImage = this.data.publishCoverImage || this.getFirstStudioImageUrl(selectedImages);
        this.setData({
          publishCoverImage: coverImage,
          selectedImages: this.ensureStudioCoverFirst(selectedImages, coverImage),
          candidateImages: this.markStudioCandidateImages(candidateImages, selectedImages)
        });
      })
      .catch(() => {
        wx.showToast({ title: '上传失败', icon: 'none' });
      });
  },

  onPublishDescInput(e) {
    this.setData({ publishDescription: e.detail.value });
  },

  onPublishContentInput(e) {
    this.setData({ publishContent: e.detail.value });
  },

  onPublishTagInput(e) {
    this.setData({ publishTagInput: e.detail.value });
  },

  addPublishTag() {
    const tag = (this.data.publishTagInput || '').trim();
    if (!tag) return;
    this.addPublishTagValue(tag);
    this.setData({ publishTagInput: '' });
  },

  togglePublishHotTag(e) {
    this.addPublishTagValue(e.currentTarget.dataset.tag);
  },

  addPublishTagValue(tag) {
    const tags = this.data.publishTags.slice();
    if (tags.includes(tag)) return;
    if (tags.length >= 6) {
      wx.showToast({ title: '最多添加6个标签', icon: 'none' });
      return;
    }
    tags.push(tag);
    this.setData({ publishTags: tags });
  },

  removePublishTag(e) {
    const tag = e.currentTarget.dataset.tag;
    this.setData({ publishTags: this.data.publishTags.filter(item => item !== tag) });
  },

  submitStudioPublish() {
    if (this.data.submittingPublish) return;
    const title = (this.data.name || this.data.destination || '').trim();
    const description = (this.data.publishDescription || this.data.description || '').trim();
    if (!title) {
      wx.showToast({ title: '请填写行程名称', icon: 'none' });
      return;
    }
    if (!this.data.publishCoverImage) {
      wx.showToast({ title: '请选择发布封面', icon: 'none' });
      return;
    }
    this.setData({ submittingPublish: true });
    api.createPublish({
      tripPlanId: this.data.tripId,
      title,
      description,
      content: (this.data.publishContent || '').trim(),
      coverImage: this.data.publishCoverImage,
      location: this.data.publishLocation || this.data.destination || '',
      days: this.data.publishDays || this.data.daysCount || 1,
      nights: this.data.publishNights || Math.max(1, (this.data.publishDays || this.data.daysCount || 1) - 1),
      tags: JSON.stringify(this.data.publishTags || []),
      images: this.ensureStudioCoverFirst(this.data.selectedImages, this.data.publishCoverImage).map((item, index) => ({
        imageUrl: item.imageUrl,
        imageType: item.imageUrl === this.data.publishCoverImage ? 'cover' : 'gallery',
        mediaType: item.mediaType || 'image',
        sourceType: item.sourceType || 'upload',
        sourceId: item.sourceId || '',
        sortOrder: index
      }))
    }).then((publishId) => {
      this.setData({ submittingPublish: false, publishId });
      app.globalData.statsDirty = true;
      wx.navigateTo({ url: `/pages/trip-detail/trip-detail?publishId=${publishId}` });
    }).catch(() => {
      this.setData({ submittingPublish: false });
      wx.showToast({ title: '发布失败', icon: 'none' });
    });
  },

  // ==================== 编辑页 - 日历同步 ====================

  syncToCalendar() {
    if (!this.data.trip || !this.data.trip.days || this.data.trip.days.length === 0) {
      wx.showToast({ title: '没有可同步的行程', icon: 'none' });
      return;
    }

    const trip = this.data.trip;
    const days = this.data.trip.days;
    let addedCount = 0;
    let failCount = 0;
    const total = days.length;

    days.forEach((day, idx) => {
      const spots = day.spots || [];
      const spotNames = spots.map(s => s.name).filter(Boolean).join('、') || ('第' + (idx + 1) + '天');
      const dateStr = day.date || '';
      const startDate = dateStr.replace(/-/g, ':').replace(' ', ':');

      wx.addPhoneCalendar({
        title: `${trip.destination || ''}之旅 · 第${day.day || idx + 1}天`,
        startTime: startDate + ' 08:00:00',
        endTime: startDate + ' 22:00:00',
        description: `今日安排：${spotNames}`,
        location: trip.destination || '',
        success: () => {
          addedCount++;
          if (addedCount + failCount >= total) {
            this.setData({ calendarSynced: true });
            wx.showModal({
              title: '同步完成',
              content: `已成功添加 ${addedCount} 天日程到手机日历`,
              showCancel: false
            });
          }
        },
        fail: () => {
          failCount++;
          if (addedCount + failCount >= total) {
            if (failCount === total) {
              wx.showToast({ title: '同步失败，请检查日历权限', icon: 'none' });
            } else {
              this.setData({ calendarSynced: true });
              wx.showToast({ title: `部分成功 (${addedCount}/${total})`, icon: 'none' });
            }
          }
        }
      });
    });
  },

  // ==================== 编辑页 - 版本管理 ====================

  openMoreActions() {
    wx.showActionSheet({
      itemList: [this.data.calendarSynced ? '已同步日历' : '同步日历', '版本管理'],
      success: (res) => {
        if (res.tapIndex === 0) {
          this.syncToCalendar();
          return;
        }
        if (res.tapIndex === 1) {
          this.showVersionPanel();
        }
      }
    });
  },

  showVersionPanel() {
    if (!this.data.tripId) return;
    api.getTripVersions(this.data.tripId)
      .then((versions) => {
        this.setData({ versions: versions || [], showVersionPanel: true });
      })
      .catch(() => {});
  },

  closeVersionPanel() {
    this.setData({ showVersionPanel: false });
  },

  saveCurrentVersion() {
    if (!this.data.tripId) return;
    wx.showModal({
      title: '保存版本',
      content: '为当前行程创建快照备份？',
      editable: true,
      placeholderText: '输入备注（可选）',
      success: (res) => {
        if (res.confirm) {
          api.saveTripVersion(this.data.tripId, res.content || '手动保存')
            .then(() => {
              this.showVersionPanel();
            })
            .catch(() => { wx.showToast({ title: '失败', icon: 'none' }); });
        }
      }
    });
  },

  restoreVersion(e) {
    const { id, name } = e.currentTarget.dataset;
    wx.showModal({
      title: '恢复版本',
      content: `确定恢复到「${name}」？当前行程会自动备份。`,
      confirmText: '确认恢复',
      confirmColor: '#2EC4B6',
      success: (res) => {
        if (res.confirm) {
          api.restoreTripVersion(this.data.tripId, id)
            .then(() => {
              this.setData({ showVersionPanel: false });
              setTimeout(() => this.loadTripDetail(this.data.tripId), 500);
            })
            .catch(() => { wx.showToast({ title: '恢复失败', icon: 'none' }); });
        }
      }
    });
  },

  // ==================== 分享 ====================

  onShareAppMessage() {
    const trip = this.data.trip;
    let title = '拾路派 - 行程编辑';
    if (trip && trip.name) title += ' · ' + trip.name;
    else if (this.data.destination) title += ' · ' + this.data.destination;
    return share.shareToFriend({
      title,
      path: '/pages/planner/planner' + (this.data.tripId ? '?id=' + this.data.tripId : '')
    });
  },

  onShareTimeline() {
    const trip = this.data.trip;
    let title = '拾路派 - 行程编辑';
    if (trip && trip.name) title += ' · ' + trip.name;
    return share.shareToTimeline({ title });
  }
});
