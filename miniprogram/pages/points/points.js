const pointsAPI = require('../../api/points');
const app = getApp();
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');

Page({
  data: {
    darkMode: (app && app.globalData && app.globalData.darkMode) || false,
    isLoggedIn: false,
    points: {},
    levelConfig: {},
    todaySigned: false,
    signInStreak: 0,
    records: [],
    recordsPage: 1,
    recordsHasMore: true,
    recordsLoading: false,
    earnTasks: [
      { source: 'publish', name: '发布旅行计划', description: '每日最多3次', points: 20 },
      { source: 'review', name: '发布点评', description: '每日最多5次', points: 10 },
      { source: 'comment', name: '评论他人', description: '每日最多10次', points: 3 },
      { source: 'follow', name: '关注他人', description: '无限制', points: 1 },
      { source: 'complete_profile', name: '完善个人资料', description: '一次性', points: 30 }
    ]
  },
  
  onShow() {
    applyTheme(this);
    this.checkLogin();
    if (this.data.isLoggedIn) {
      this.loadPoints()
        .then(() => {
          // 等 points 加载完成后再计算等级和签到状态
          this.loadLevelConfig();
          this.checkTodaySigned();
        });
      this.loadRecords();
    }
    // 启用分享
    share.enableShareMenu();
  },
  
  checkLogin() {
    const loggedIn = app.globalData.isLoggedIn || false;
    this.setData({ isLoggedIn: loggedIn });
  },
  
  goLogin() {
    wx.navigateTo({
      url: '/pages/login/login'
    });
  },
  
  loadPoints() {
    if (!this.data.isLoggedIn) return Promise.resolve();
    return pointsAPI.getMyPoints()
      .then((data) => {
        this.setData({
          points: data,
          signInStreak: data.signInStreak || 0
        });
      })
      .catch((err) => {
        console.error('加载积分失败:', err);
      });
  },
  
  loadRecords() {
    const { recordsPage, recordsLoading } = this.data;
    if (recordsLoading) return;
    this.setData({ recordsLoading: true });
    pointsAPI.getPointRecords(recordsPage, 20)
      .then((data) => {
        const records = recordsPage === 1 ? (data || []) : [...this.data.records, ...(data || [])];
        this.setData({
          records,
          recordsHasMore: (data || []).length === 20,
          recordsLoading: false
        });
      })
      .catch((err) => {
        console.error('加载记录失败:', err);
        this.setData({ recordsLoading: false });
      });
  },

  onReachBottom() {
    if (!this.data.recordsHasMore || this.data.recordsLoading) return;
    this.setData({ recordsPage: this.data.recordsPage + 1 }, () => this.loadRecords());
  },

  onPullDownRefresh() {
    this.loadPoints()
      .then(() => {
        this.loadLevelConfig();
        this.checkTodaySigned();
      });
    this.setData({ recordsPage: 1, recordsHasMore: true });
    this.loadRecords();
    wx.stopPullDownRefresh();
  },
  
  loadLevelConfig() {
    // 从服务器获取等级配置动态计算等级
    pointsAPI.getLevelConfig()
      .then((levels) => {
        if (!levels || levels.length === 0) {
          // 降级：使用硬编码阈值
          this._calcLevelFallback();
          return;
        }
        const points = this.data.points.totalPoints || 0;
        // 按 minPoints 降序排列，找到第一个匹配的等级
        const sorted = levels.sort((a, b) => b.minPoints - a.minPoints);
        let matched = sorted[sorted.length - 1] || { level: 1, title: '旅游新手' };
        for (const lv of sorted) {
          if (points >= lv.minPoints) {
            matched = lv;
            break;
          }
        }
        this.setData({
          levelConfig: { level: matched.level, title: matched.title }
        });
      })
      .catch(() => {
        // 接口失败时降级为本地硬编码
        this._calcLevelFallback();
      });
  },

  /** 降级：本地硬编码等级计算 */
  _calcLevelFallback() {
    const points = this.data.points.totalPoints || 0;
    let level = 1;
    let title = '旅游新手';
    if (points >= 20000) { level = 5; title = '拾路传说'; }
    else if (points >= 5000) { level = 4; title = '旅行大师'; }
    else if (points >= 1000) { level = 3; title = '旅行达人'; }
    else if (points >= 200) { level = 2; title = '旅行爱好者'; }
    this.setData({ levelConfig: { level, title } });
  },
  
  checkTodaySigned() {
    const today = new Date().toISOString().split('T')[0];
    const lastSignIn = this.data.points.lastSignInDate;
    this.setData({
      todaySigned: lastSignIn === today
    });
  },
  
  doSignIn() {
    if (!this.data.isLoggedIn) {
      wx.showModal({
        title: '提示',
        content: '请先登录后再签到',
        confirmText: '去登录',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({
              url: '/pages/login/login'
            });
          }
        }
      });
      return;
    }
    
    if (this.data.todaySigned) return;
    
    pointsAPI.signIn()
      .then((data) => {
        this.setData({
          todaySigned: true,
          signInStreak: data.streak
        });
        this.loadPoints();
      })
      .catch((err) => {
        wx.showToast({
          title: err.message || '签到失败',
          icon: 'none'
        });
      });
  },
  
  /**
   * 点击"去完成"按钮，跳转到对应页面
   */
  goTask(e) {
    if (!this.data.isLoggedIn) {
      wx.showModal({
        title: '提示',
        content: '请先登录',
        confirmText: '去登录',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/login/login' });
          }
        }
      });
      return;
    }
    
    const source = e.currentTarget.dataset.source;
    
    switch (source) {
      case 'publish':
        // L8: 发布旅行计划 → 跳转到行程 Tab（从这里可以发布行程）
        wx.switchTab({
          url: '/pages/my-trips/my-trips'
        });
        break;
        
      case 'review':
      case 'comment':
        // L8: 发布点评/评论 → 跳转到发现页（广场里有行程可以评论）
        wx.switchTab({
          url: '/pages/index/index'
        });
        break;
        
      case 'follow':
        // L8: 关注他人 → 跳转到发现页，进入他人主页后关注
        wx.switchTab({
          url: '/pages/index/index'
        });
        break;
        
      case 'complete_profile':
        // 完善个人资料 → 跳转到编辑资料页
        wx.navigateTo({
          url: '/pages/edit-profile/edit-profile'
        });
        break;
        
      default:
        wx.showToast({
          title: '请前往对应页面完成',
          icon: 'none'
        });
    }
  },

  // ==================== 分享 ====================

  onShareAppMessage() {
    const level = this.data.levelConfig;
    const pts = this.data.points;
    const levelTitle = level.title || '旅游新手';
    const totalPts = pts.totalPoints || 0;
    let title = '拾路派 - 积分中心';
    if (totalPts > 0) {
      title = '我在拾路派达到「' + levelTitle + '」等级，累计' + totalPts + '积分！';
    }
    return share.shareToFriend({ title, path: '/pages/points/points' });
  },

  onShareTimeline() {
    const level = this.data.levelConfig;
    const pts = this.data.points;
    const levelTitle = level.title || '旅游新手';
    const totalPts = pts.totalPoints || 0;
    let title = '拾路派 - 积分中心';
    if (totalPts > 0) {
      title = '拾路派 ' + levelTitle + '  |  累计' + totalPts + '积分';
    }
    return share.shareToTimeline({ title });
  }
});
