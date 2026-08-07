const app = getApp();
const api = require('../../utils/api');
const cache = require('../../utils/cache');
const { applyTheme } = require('../../utils/theme');

Page({
  data: {
    isLoggedIn: false,
    notificationsEnabled: true,
    locationEnabled: true,
    darkMode: false,
    manualDarkOverride: false,
    travelModes: ['自驾', '步行', '公共交通', '打车'],
    travelModeIndex: 0,
    budgetLevels: ['经济型', '中端型', '高端型'],
    budgetIndex: 1,
    styles: ['休闲佛系', '深度打卡', '小众探秘', '亲子游玩', '美食专项'],
    styleIndex: 0
  },

  onLoad() {
    applyTheme(this);
    this.setData({
      isLoggedIn: app.globalData.isLoggedIn,
      darkMode: app.globalData.darkMode,
      manualDarkOverride: app.globalData.themeFollowSystem === false && app.globalData.darkMode === true
    });
    this.loadSettings();
    this.loadPreferences();
  },

  onShow() {
    applyTheme(this);
    this.setData({
      isLoggedIn: app.globalData.isLoggedIn,
      darkMode: app.globalData.darkMode,
      manualDarkOverride: app.globalData.themeFollowSystem === false && app.globalData.darkMode === true
    });
  },

  loadSettings() {
    // 本地优先：先读缓存
    const cached = cache.get('user_settings');
    if (cached && cached.notificationsEnabled !== undefined) {
      this.setData({ notificationsEnabled: !!cached.notificationsEnabled });
    }

    api.getUserSettings()
      .then((jsonStr) => {
        if (!jsonStr || jsonStr === '{}') return;
        let settings;
        if (typeof jsonStr === 'string') {
          try { settings = JSON.parse(jsonStr); } catch (e) { return; }
        } else {
          settings = jsonStr;
        }
        if (settings.notificationsEnabled !== undefined) {
          const enabled = !!settings.notificationsEnabled;
          this.setData({ notificationsEnabled: enabled });
          // 静默覆盖缓存
          cache.set('user_settings', { notificationsEnabled: enabled });
        }
      })
      .catch(() => {});
  },

  loadPreferences() {
    const preferences = wx.getStorageSync('preferences');
    if (preferences) {
      this.setData({
        travelModeIndex: preferences.travelModeIndex || 0,
        budgetIndex: preferences.budgetIndex || 1,
        styleIndex: preferences.styleIndex || 0
      });
    }
    api.getPreferences()
      .then((jsonStr) => {
        if (!jsonStr || jsonStr === '{}') return;
        let serverPrefs;
        if (typeof jsonStr === 'string') {
          try { serverPrefs = JSON.parse(jsonStr); } catch (e) { return; }
        } else {
          serverPrefs = jsonStr;
        }
        this.setData({
          travelModeIndex: serverPrefs.travelModeIndex ?? this.data.travelModeIndex,
          budgetIndex: serverPrefs.budgetIndex ?? this.data.budgetIndex,
          styleIndex: serverPrefs.styleIndex ?? this.data.styleIndex
        });
        wx.setStorageSync('preferences', {
          travelModeIndex: this.data.travelModeIndex,
          budgetIndex: this.data.budgetIndex,
          styleIndex: this.data.styleIndex
        });
      })
      .catch(() => {});
  },

  onTravelModeChange(e) {
    this.setData({ travelModeIndex: parseInt(e.detail.value) });
    this.savePreferences();
  },

  onBudgetChange(e) {
    this.setData({ budgetIndex: parseInt(e.detail.value) });
    this.savePreferences();
  },

  onStyleChange(e) {
    this.setData({ styleIndex: parseInt(e.detail.value) });
    this.savePreferences();
  },

  savePreferences() {
    const preferences = {
      travelModeIndex: this.data.travelModeIndex,
      budgetIndex: this.data.budgetIndex,
      styleIndex: this.data.styleIndex
    };
    wx.setStorageSync('preferences', preferences);
    api.savePreferences(preferences).catch((err) => {
      console.warn('[settings] 保存出行偏好失败:', err);
      wx.showToast({ title: '偏好保存失败', icon: 'none' });
    });
  },

  onNotificationChange(e) {
    const enabled = e.detail.value;
    this.setData({ notificationsEnabled: enabled });
    api.saveUserSettings({ notificationsEnabled: enabled }).catch((err) => {
      console.warn('[settings] 保存通知设置失败:', err);
      this.setData({ notificationsEnabled: !enabled });
      wx.showToast({ title: '保存失败，请重试', icon: 'none' });
    });
  },

  onLocationChange(e) {
    this.setData({ locationEnabled: e.detail.value });
    if (e.detail.value) {
      wx.authorize({
        scope: 'scope.userLocation',
        success: () => {
          // 定位已开启
        }
      });
    }
  },

  onDarkModeChange(e) {
    const checked = e.detail.value;
    this.setData({ manualDarkOverride: checked });
    if (checked) {
      app.setTheme(true);
      this.setData({ darkMode: true });
    } else {
      app.resetThemeToSystem();
      this.setData({ darkMode: app.globalData.darkMode });
    }
  },

  showAbout() {
    wx.navigateTo({ url: '/pages/about/about' });
  },

  showHelp() {
    wx.navigateTo({ url: '/pages/help/help' });
  },

  checkUpdate() {
    const updateManager = wx.getUpdateManager();
    updateManager.onCheckForUpdate({
      success: (res) => {
        if (res.hasUpdate) {
          wx.showModal({
            title: '发现新版本',
            content: '是否立即更新？',
            success: (modalRes) => {
              if (modalRes.confirm) {
                updateManager.onUpdateReady(() => {
                  wx.showModal({
                    title: '更新提示',
                    content: '新版本已经准备好，是否重启应用？',
                    success: (restartRes) => {
                      if (restartRes.confirm) updateManager.applyUpdate();
                    }
                  });
                });
              }
            }
          });
        } else {
          // 已是最新版本
        }
      },
      fail: () => {
        wx.showToast({ title: '检查失败', icon: 'none' });
      }
    });
  },

  /** 清除缓存（私信记录、会话列表、页面缓存等，保留个人信息） */
  clearCache() {
    const info = cache.getCacheInfo();
    if (info.count === 0) {
      wx.showToast({ title: '暂无缓存数据', icon: 'none' });
      return;
    }

    wx.showModal({
      title: '清理缓存',
      content: '将清除聊天记录、会话列表、首页缓存等数据。不会清除个人信息和登录状态。确定要清除吗？',
      confirmText: '确定',
      confirmColor: '#EF4444',
      success: (res) => {
        if (!res.confirm) return;
        const result = cache.clearAll();
        if (result.success) {
          // 缓存已清除
        } else {
          wx.showToast({ title: '清除失败', icon: 'none' });
        }
      }
    });
  },

  logout() {
    wx.showModal({
      title: '确认退出',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.logout();
          wx.reLaunch({ url: '/pages/login/login' });
        }
      }
    });
  }
});
