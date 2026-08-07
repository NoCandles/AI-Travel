/**
 * 拾路派 - 全局AI助手悬浮按钮
 * 初始固定右下角，可拖动，点击跳转聊天
 */
const app = getApp();

Component({
  properties: {
    hidden: {
      type: Boolean,
      value: false
    }
  },

  data: {
    x: 0,
    y: 0,
    screenWidth: 375,
    screenHeight: 667,
    fabSize: 56,
    animation: false // 初始禁用动画
  },

  lifetimes: {
    attached() {
      const sysInfo = wx.getWindowInfo();
      const screenWidth = sysInfo.windowWidth;
      const screenHeight = sysInfo.windowHeight;
      const fabSize = this.data.fabSize;
      
      // 初始位置：右下角
      const x = screenWidth - fabSize - 16;
      const y = screenHeight - fabSize - 120;
      
      this.setData({
        screenWidth,
        screenHeight,
        x,
        y
      });
      
      // 渲染完成后启用动画
      setTimeout(() => {
        this.setData({ animation: true });
      }, 100);
      
      // 记录初始位置用于判断拖动
      this._hasMoved = false;
      this._startX = x;
      this._startY = y;
    }
  },

  methods: {
    onTouchStart(e) {
      // 记录触摸起始位置
      this._touchStartX = e.touches[0].clientX;
      this._touchStartY = e.touches[0].clientY;
      this._hasMoved = false;
    },

    onChange(e) {
      const newX = e.detail.x;
      const newY = e.detail.y;
      
      // 记录当前位置
      this._currentX = newX;
      this._currentY = newY;
    },

    onTouchEnd(e) {
      // 计算触摸移动距离
      const touchEndX = e.changedTouches[0].clientX;
      const touchEndY = e.changedTouches[0].clientY;
      const moveDistance = Math.sqrt(
        Math.pow(touchEndX - this._touchStartX, 2) + 
        Math.pow(touchEndY - this._touchStartY, 2)
      );
      
      // 如果移动距离小于 10px，认为是点击
      if (moveDistance < 10) {
        this._handleTap();
        return;
      }
      
      // 拖动结束，保存位置
      wx.setStorageSync('fab_position', {
        x: this._currentX || this.data.x,
        y: this._currentY || this.data.y
      });
    },

    _handleTap() {
      if (!app.globalData.token) {
        wx.showModal({
          title: '提示',
          content: '请先登录后使用',
          confirmText: '去登录',
          showCancel: true,
          success: (res) => {
            if (res.confirm) {
              wx.navigateTo({ url: '/pages/login/login' });
            }
          }
        });
        return;
      }

      wx.navigateTo({
        url: '/pages/chat/chat',
        fail: () => {}
      });
    }
  }
});
