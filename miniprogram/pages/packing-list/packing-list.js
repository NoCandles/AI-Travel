const app = getApp();
const api = require('../../utils/api');
const tripAPI = require('../../api/trip');
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');

// 防抖定时器 ID
let saveTimer = null;

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    destination: '',
    tripId: '',
    canEdit: false,
    categories: [],
    progress: { checked: 0, total: 0 },
    loading: false,

    // 弹窗
    showModal: false,
    modalType: 'add',
    modalCatIdx: 0,
    modalName: '',
    modalQty: 1,
    modalNote: '',
    editingCatIdx: -1,
    editingItemIdx: -1
  },

  onLoad(options) {
    applyTheme(this);

    // ========== ✅ 从参数直接读取行李清单数据 ==========
    // 不再调用独立 API，数据由上一页（trip-detail/index）传递过来
    let categories = [];
    try {
      if (options.packingList) {
        categories = JSON.parse(decodeURIComponent(options.packingList));
      }
    } catch (e) {
      console.warn('[packing-list] 解析 packingList 参数失败:', e);
    }

    this.setData({
      destination: options.destination ? decodeURIComponent(options.destination) : '',
      days: parseInt(options.days) || 1,
      travelMode: options.travelMode || 'drive',
      tripId: options.tripId || '',
      canEdit: options.canEdit === '1',
      categories: this.normalizeCategories(categories)
    });

    this.updateProgress();

    // 兜底：未传 packingList 但有 tripId（例如从 chat 页直接跳来），调 API 加载
    if ((!categories || categories.length === 0) && options.tripId) {
      this.loadPackingListFromServer(options.tripId);
    }

    // 启用分享
    share.enableShareMenu();
  },

  /** 通过 tripId 拉取已存储的行李清单 */
  loadPackingListFromServer(tripId) {
    this.setData({ loading: true });
    tripAPI.getPackingListByTripId(tripId)
      .then((res) => {
        const list = (res && (res.packingList || res.categories || res)) || [];
        const normalized = this.normalizeCategories(list);
        this.setData({ categories: normalized, loading: false });
        this.updateProgress();
      })
      .catch((err) => {
        console.warn('[packing-list] 拉取行李清单失败:', err);
        this.setData({ loading: false });
      });
  },

  onShow() {
    applyTheme(this);
  },

  // ==================== 数据工具 ====================

  /** 标准化分类数据 */
  normalizeCategories(categories) {
    return (categories || []).map(cat => ({
      name: cat.name || cat.category || '未分类',
      items: (cat.items || []).map(item => ({
        name: item.name || '未知物品',
        quantity: typeof item.quantity === 'number' ? item.quantity : 1,
        checked: !!item.checked,
        note: item.note || ''
      })),
      checkedCount: cat.items ? cat.items.filter(i => i.checked).length : 0
    }));
  },

  /** 更新进度 */
  updateProgress() {
    let checked = 0, total = 0;
    this.data.categories.forEach(cat => {
      (cat.items || []).forEach(item => {
        total++;
        if (item.checked) checked++;
      });
    });
    this.setData({ progress: { checked, total } });
  },

  // ==================== 勾选操作 ====================

  toggleItem(e) {
    const { catIdx, itemIdx } = e.currentTarget.dataset;
    const cat = this.data.categories[catIdx];
    const items = cat.items.map((it, i) => {
      if (i === itemIdx) return { ...it, checked: !it.checked };
      return it;
    });
    const checkedCount = items.filter(i => i.checked).length;

    this.setData({
      [`categories[${catIdx}].items`]: items,
      [`categories[${catIdx}].checkedCount`]: checkedCount
    });
    this.updateProgress();

    // 防抖保存
    this.debounceSave();
  },

  resetAll() {
    wx.showModal({
      title: '重置清单',
      content: '确定要重置所有勾选状态吗？',
      success: (res) => {
        if (!res.confirm) return;
        const newCategories = this.data.categories.map(cat => ({
          ...cat,
          items: cat.items.map(item => ({ ...item, checked: false })),
          checkedCount: 0
        }));
        this.setData({ categories: newCategories });
        this.updateProgress();
        this.debounceSave();
      }
    });
  },

  goBack() {
    wx.navigateBack();
  },

  // ==================== 保存（防抖 + updateTrip） ====================

  /** 防抖触发保存（300ms 内多次操作只保存一次） */
  debounceSave() {
    if (!this.data.tripId) return;
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(() => this.saveToServer(), 300);
  },

  /** 保存行李清单到后端 —— 通过 updateTrip 统一保存 */
  saveToServer() {
    const { tripId, categories } = this.data;
    if (!tripId) return;

    api.updateTrip(tripId, { packingList: categories })
      .then(() => {
      })
      .catch((err) => {
        console.error('[packing-list] 保存失败:', err);
      });
  },

  // ==================== 重新生成 ====================

  /** 重新生成行李清单（调用 API） */
  regeneratePackingList() {
    if (!this.data.tripId) {
      wx.showToast({ title: '请先生成行程', icon: 'none' });
      return;
    }

    wx.showModal({
      title: '重新生成清单',
      content: '将根据行程信息重新生成行李清单，手动添加的物品会丢失。是否继续？',
      confirmText: '重新生成',
      success: (res) => {
        if (!res.confirm) return;

        api.regeneratePackingList(this.data.tripId)
          .then((trip) => {
            if (trip && trip.packingList) {
              this.setData({ categories: this.normalizeCategories(trip.packingList) });
              this.updateProgress();
            } else {
              wx.showToast({ title: '生成失败', icon: 'none' });
            }
          })
          .catch(() => {
            wx.showToast({ title: '生成失败', icon: 'none' });
          });
      }
    });
  },

  // ==================== CRUD 弹窗 ====================

  showAddModal() {
    this.setData({
      showModal: true,
      modalType: 'add',
      modalCatIdx: 0,
      modalName: '',
      modalQty: 1,
      modalNote: ''
    });
  },

  editItem(e) {
    const { catIdx, itemIdx } = e.currentTarget.dataset;
    const item = this.data.categories[catIdx].items[itemIdx];
    this.setData({
      showModal: true,
      modalType: 'edit',
      modalCatIdx: catIdx,
      modalName: item.name,
      modalQty: item.quantity,
      modalNote: item.note,
      editingCatIdx: catIdx,
      editingItemIdx: itemIdx
    });
  },

  hideModal() {
    this.setData({ showModal: false });
  },

  stopPropagation() {},

  onCatChange(e) {
    this.setData({ modalCatIdx: parseInt(e.detail.value) });
  },

  onNameInput(e) {
    this.setData({ modalName: e.detail.value });
  },

  onQtyInput(e) {
    this.setData({ modalQty: parseInt(e.detail.value) || 1 });
  },

  onNoteInput(e) {
    this.setData({ modalNote: e.detail.value });
  },

  // ✅ 修复 B4：不再直接修改 this.data，用不可变数据 + setData
  saveModal() {
    const { modalType, modalCatIdx, modalName, modalQty, modalNote,
            editingCatIdx, editingItemIdx } = this.data;

    if (!modalName.trim()) {
      wx.showToast({ title: '请输入物品名称', icon: 'none' });
      return;
    }

    // 深拷贝 categories（不可变更新）
    const newCategories = this.data.categories.map(cat => ({
      ...cat,
      items: cat.items.map(it => ({ ...it }))
    }));

    if (modalType === 'add') {
      // 新增物品
      if (newCategories.length === 0) {
        newCategories.push({ name: '自定义', items: [], checkedCount: 0 });
      }
      const idx = modalCatIdx < newCategories.length ? modalCatIdx : 0;
      newCategories[idx].items.push({
        name: modalName.trim(),
        quantity: modalQty,
        checked: false,
        note: modalNote.trim()
      });
      newCategories[idx].checkedCount = newCategories[idx].items.filter(i => i.checked).length;
    } else {
      // 编辑物品
      newCategories[editingCatIdx].items[editingItemIdx] = {
        ...newCategories[editingCatIdx].items[editingItemIdx],
        name: modalName.trim(),
        quantity: modalQty,
        note: modalNote.trim()
      };
    }

    this.setData({ categories: newCategories, showModal: false });
    this.updateProgress();
    this.debounceSave();
  },

  /** 删除物品 */
  deleteItem(e) {
    const { catIdx, itemIdx } = e.currentTarget.dataset;

    wx.showModal({
      title: '删除物品',
      content: '确定要删除这个物品吗？',
      success: (res) => {
        if (!res.confirm) return;

        // 不可变更新
        const newCategories = this.data.categories.map(cat => ({
          ...cat,
          items: [...cat.items]
        }));
        newCategories[catIdx].items.splice(itemIdx, 1);
        newCategories[catIdx].checkedCount = newCategories[catIdx].items.filter(i => i.checked).length;

        this.setData({ categories: newCategories });
        this.updateProgress();
        this.debounceSave();
      }
    });
  },

  // ==================== 分享 ====================

  onShareAppMessage() {
    const { destination, progress } = this.data;
    let title = '拾路派 - 行李清单';
    if (destination) title += ' · ' + destination + '之旅';
    if (progress.total > 0) title += ' （' + progress.checked + '/' + progress.total + '）';
    return share.shareToFriend({
      title,
      path: '/pages/packing-list/packing-list?tripId=' + this.data.tripId + '&destination=' + encodeURIComponent(destination || '')
    });
  },

  onShareTimeline() {
    const { destination } = this.data;
    let title = '拾路派 - 行李清单';
    if (destination) title += ' · ' + destination + '之旅';
    return share.shareToTimeline({ title });
  }
});
