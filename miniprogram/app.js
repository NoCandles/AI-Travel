const C = require('./config/constants');

App({
  onLaunch() {
    wx.cloud.init({ env: C.CLOUD_ENV_ID });
    this.initUserInfo();
    this.initTheme();
    this.applyGlobalTheme();
    this.checkUpdate();
    this.initErrorMonitor();
    this.initNetwork();
  },

  globalData: {
    userInfo: null,
    isLoggedIn: false,
    token: null,
    darkMode: false,
    themeFollowSystem: true,   // 是否跟随系统主题（true=跟随，false=用户手动设置）
    tripListDirty: true,
    statsDirty: true,
    squareDirty: true,   // 发现页脏标记，true 时需要刷新
    currentTrip: null,
    mustGoSpots: [],
    preferences: {
      travelMode: 'drive',
      budget: 'medium',
      style: 'leisure'
    },
    cloudEnvId: C.CLOUD_ENV_ID,
    cloudServiceName: C.CLOUD_SERVICE_NAME,
    debugMode: C.DEBUG_MODE,
    localBaseUrl: C.LOCAL_BASE_URL,
    localWsBaseUrl: C.LOCAL_WS_BASE_URL,
    cloudBaseUrl: C.CLOUD_BASE_URL,
    cloudWsBaseUrl: C.CLOUD_WS_BASE_URL,
    pointsEnabled: C.POINTS_ENABLED,
    // 注意：tmapKey 和 deepSeekKey 等敏感凭据已在服务端管理，前端不再持有
  },

  initUserInfo() {
    const userInfo = wx.getStorageSync('userInfo');
    const token = wx.getStorageSync('token');
    if (userInfo && token) {
      this.globalData.userInfo = userInfo;
      this.globalData.token = token;
      this.globalData.isLoggedIn = true;
    }
  },

  initTheme() {
    // 读取用户手动设置
    const stored = wx.getStorageSync('darkMode');
    const hasManualPref = stored === true || stored === false;
    if (hasManualPref) {
      // 用户手动设置过 → 以用户设置为准
      this.globalData.darkMode = !!stored;
      this.globalData.themeFollowSystem = false;
    } else {
      // 未手动设置 → 跟随系统
      try {
        const sysTheme = (wx.getSystemInfoSync().theme) || 'light';
        this.globalData.darkMode = sysTheme === 'dark';
      } catch (e) {
        this.globalData.darkMode = false;
      }
      this.globalData.themeFollowSystem = true;
    }

    // 监听系统主题变化（仅当跟随系统时同步）
    if (wx.onThemeChange) {
      wx.onThemeChange((res) => {
        if (this.globalData.themeFollowSystem) {
          this.globalData.darkMode = res.theme === 'dark';
          this.applyGlobalTheme();
          // 通知所有活动页面更新 data.darkMode
          this._broadcastThemeChange();
        }
      });
    }
  },

  /** 通知所有活动页面更新 darkMode 状态 */
  _broadcastThemeChange() {
    const pages = getCurrentPages();
    pages.forEach((page) => {
      if (page && page.setData) {
        page.setData({ darkMode: this.globalData.darkMode });
      }
    });
  },

  applyGlobalTheme() {
    const dark = this.globalData.darkMode;
    wx.setBackgroundColor({
      backgroundColor: dark ? '#0f1117' : '#f8fafc',
      backgroundColorTop: dark ? '#0f1117' : '#ffffff',
      backgroundColorBottom: dark ? '#0f1117' : '#f8fafc'
    });
    wx.setTabBarStyle({
      color: dark ? '#5b5f6b' : '#94a3b8',
      selectedColor: '#2EC4B6',
      backgroundColor: dark ? '#13151c' : '#FFFFFF',
      borderStyle: dark ? 'black' : 'white'
    });
    wx.setNavigationBarColor({
      frontColor: dark ? '#ffffff' : '#ffffff',
      backgroundColor: dark ? '#0f1117' : '#2EC4B6',
      animation: { duration: 300, timingFunc: 'easeInOut' }
    });
  },

  setTheme(darkMode) {
    // 用户手动设置 → 不再跟随系统
    this.globalData.darkMode = !!darkMode;
    this.globalData.themeFollowSystem = false;
    wx.setStorageSync('darkMode', !!darkMode);
    this.applyGlobalTheme();
  },

  /** 重置为跟随系统主题（用于"跟随系统"选项） */
  resetThemeToSystem() {
    wx.removeStorageSync('darkMode');
    this.globalData.themeFollowSystem = true;
    try {
      const sysTheme = (wx.getSystemInfoSync().theme) || 'light';
      this.globalData.darkMode = sysTheme === 'dark';
    } catch (e) {
      this.globalData.darkMode = false;
    }
    this.applyGlobalTheme();
    this._broadcastThemeChange();
  },

  checkUpdate() {
    try {
      const updateManager = wx.getUpdateManager();
      // 先注册 onUpdateReady（避免版本检查后才监听更新）
      updateManager.onUpdateReady(() => {
        wx.showModal({
          title: '更新提示',
          content: '新版本已经准备好，是否重启应用？',
          success: (res) => {
            if (res.confirm) {
              updateManager.applyUpdate();
            }
          }
        });
      });
      updateManager.onCheckForUpdate((res) => {
        if (res.hasUpdate) {
        }
      });
    } catch (e) {
      console.warn('更新管理器不可用:', e);
    }
  },

  /** 初始化网络状态监听 */
  initNetwork() {
    try {
      const network = require('./utils/network');
      network.init();
      // 同步到 globalData，方便页面读取
      this.globalData.isOnline = network.isOnline();
      network.onNetworkChange((online) => {
        this.globalData.isOnline = online;
      });
    } catch (e) {
      console.warn('[网络] 初始化失败:', e);
    }
  },

  /** 全局错误监控 */
  initErrorMonitor() {
    try {
      wx.onError((error) => {
        console.error('[全局错误]', error);
        // 可在此上报错误到后端日志服务
      });
      wx.onUnhandledRejection((res) => {
        console.error('[未处理的 Promise 拒绝]', res.reason);
      });
    } catch (e) {
      console.warn('错误监控初始化失败:', e);
    }
  },

  setUserInfo(userInfo, token) {
    this.globalData.userInfo = userInfo;
    this.globalData.isLoggedIn = true;
    if (token) {
      this.globalData.token = token;
      wx.setStorageSync('token', token);
    }
    wx.setStorageSync('userInfo', userInfo);
  },

  logout() {
    this.globalData.userInfo = null;
    this.globalData.isLoggedIn = false;
    this.globalData.token = null;
    // 清除深色模式偏好（下次登录重新选择，默认跟随系统）
    this.globalData.darkMode = false;
    this.globalData.themeFollowSystem = true;
    wx.removeStorageSync('darkMode');
    // 清除所有个人信息和本地缓存数据
    ['userInfo', 'token', 'userId', 'phone', 'preferences', 'myTrips', 'tripDetail', 'historyTrips', 'selectedHotel']
      .forEach(k => wx.removeStorageSync(k));

    // 清除所有页面级缓存（cache_ 前缀的键）
    try {
      const info = wx.getStorageInfoSync();
      (info.keys || []).forEach(key => {
        if (key.startsWith('cache_')) {
          wx.removeStorageSync(key);
        }
      });
    } catch (e) {
      // ignore
    }
  }
});
