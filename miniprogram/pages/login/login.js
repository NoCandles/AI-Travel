const app = getApp();
const api = require('../../utils/api');
const { sha256 } = require('../../utils/crypto');
const { applyTheme } = require('../../utils/theme');

Page({
  _password: '',
  _resetPassword: '',

  data: {
    agreed: false,
    loading: false,
    phone: '',
    showPassword: false,
    // 忘记密码
    showForgot: false,
    resetPhone: '',
    resetLoading: false,
    darkMode: false
  },

  onLoad() {
    applyTheme(this);
    if (app.globalData.isLoggedIn && app.globalData.userInfo) {
      wx.reLaunch({ url: '/pages/index/index' });
    }
  },

  onShow() {
    applyTheme(this);
  },

  // 协议勾选
  onAgreeChange(e) {
    this.setData({
      agreed: e.detail.value.length > 0
    });
  },

  // ==================== 微信手机号一键登录 ====================
  onWechatPhoneLogin(e) {
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      wx.showToast({ title: '已取消', icon: 'none' });
      return;
    }

    if (!this.data.agreed) {
      wx.showToast({ title: '请先同意用户协议', icon: 'none' });
      return;
    }

    // 用微信 code + 加密手机号数据换取登录凭证
    wx.login({
      success: (loginRes) => {
        api.wechatPhoneLogin(loginRes.code, e.detail)
          .then((res) => this.onLoginSuccess(res))
          .catch((err) => this.handleLoginError(err));
      },
      fail: () => {
        wx.showToast({ title: '登录失败', icon: 'none' });
      }
    });
  },

  // ==================== 手机号+密码登录 ====================
  handleLogin() {
    // 检查协议
    if (!this.data.agreed) {
      wx.showToast({ title: '请先同意用户协议', icon: 'none' });
      return;
    }

    // 防止重复点击
    if (this.data.loading) return;

    const phone = this.data.phone.trim();
    const password = this._password;

    // 校验手机号
    if (!phone) {
      wx.showToast({ title: '请输入手机号', icon: 'none' });
      return;
    }
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' });
      return;
    }

    // 校验密码
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }
    if (password.length < 6 || password.length > 32) {
      wx.showToast({ title: '密码长度需在6-32位之间', icon: 'none' });
      return;
    }

    this.setData({ loading: true });

    // 客户端对密码进行 SHA256 哈希，避免明文密码在网络传输
    const hashedPassword = sha256(password);

    // 获取微信 code，用于绑定 openId（登录/注册时自动绑定）
    wx.login({
      success: (loginRes) => {
        if (loginRes.code) {
          const code = loginRes.code;
          api.loginByPhone(phone, hashedPassword, code)
            .then((res) => {
              this.onLoginSuccess(res);
            })
            .catch((err) => {
              console.error('登录失败:', err);
              this.setData({ loading: false });
              const errMsg = (err && err.message) || '登录失败，请重试';
              wx.showModal({
                title: '登录失败',
                content: errMsg,
                showCancel: false
              });
            });
        } else {
          // wx.login 失败，降级为无 code 登录
          console.warn('wx.login 失败，使用无 code 登录');
          api.loginByPhone(phone, hashedPassword)
            .then((res) => this.onLoginSuccess(res))
            .catch((err) => this.handleLoginError(err));
        }
      },
      fail: () => {
        // wx.login 调用失败，降级为无 code 登录
        console.warn('wx.login 调用失败');
        api.loginByPhone(phone, hashedPassword)
          .then((res) => this.onLoginSuccess(res))
          .catch((err) => this.handleLoginError(err));
      }
    });
  },

  // ==================== 登录成功处理 ====================
  onLoginSuccess(res) {
    const userId = res.userId;
    const token = res.token;

    wx.setStorageSync('userId', userId);
    wx.setStorageSync('token', token);

    // 缓存手机号
    if (res.phone) {
      wx.setStorageSync('phone', res.phone);
    }

    const userInfo = {
      id: userId,
      nickName: res.nickname || '用户' + (this.data.phone.slice(-4)),
      avatarUrl: res.avatar || '',
      phone: res.phone || '',
      loginTime: Date.now()
    };

    app.setUserInfo(userInfo, token);

    this.setData({
      loading: false,
      phone: ''
    });
    this._password = '';

    wx.reLaunch({ url: '/pages/index/index' });
  },

  handleLoginError(err) {
    this.setData({ loading: false });
    const errMsg = (err && err.message) || '登录失败，请重试';
    wx.showModal({
      title: '登录失败',
      content: errMsg,
      showCancel: false
    });
  },

  // ==================== 输入处理 ====================
  onPhoneInput(e) {
    this.setData({
      phone: e.detail.value
    });
  },

  onPasswordInput(e) {
    // 密码存入 JS 闭包，不经过 setData 进入视图层
    this._password = e.detail.value;
  },

  togglePassword() {
    this.setData({
      showPassword: !this.data.showPassword
    });
  },

  // ==================== 游客模式 ====================
  handleGuestLogin() {
    api.guestLogin()
      .then((res) => {
        const userId = res.userId;
        const token = res.token;

        wx.setStorageSync('userId', userId);
        wx.setStorageSync('token', token);

        const userInfo = {
          nickName: '游客',
          avatarUrl: '',
          loginTime: Date.now()
        };

        app.setUserInfo(userInfo, token);

        wx.reLaunch({ url: '/pages/index/index' });
      })
      .catch((err) => {
        console.error('游客登录失败:', err);
        wx.showToast({ title: '进入失败', icon: 'none' });
      });
  },

  // ==================== 工具方法 ====================
  viewAgreement() {
    wx.showModal({
      title: '用户协议',
      content: '欢迎使用拾路派。本协议是您与拾路派之间关于使用本小程序服务所订立的协议。使用本服务即表示您同意本协议的全部条款。',
      showCancel: false,
      confirmText: '知道了'
    });
  },

  viewPrivacy() {
    wx.showModal({
      title: '隐私政策',
      content: '我们重视您的隐私。您的个人信息仅用于提供行程规划服务，不会与第三方共享。您可以随时在设置中管理您的授权。',
      showCancel: false,
      confirmText: '知道了'
    });
  },

  // ==================== 忘记密码 ====================
  onToggleForgot() {
    this.setData({ showForgot: !this.data.showForgot });
  },

  onResetPhoneInput(e) {
    this.setData({ resetPhone: e.detail.value });
  },

  onResetPasswordInput(e) {
    this._resetPassword = e.detail.value;
  },

  handleResetPassword() {
    const phone = this.data.resetPhone.trim();
    const newPassword = this._resetPassword;

    // 校验手机号
    if (!phone) {
      wx.showToast({ title: '请输入手机号', icon: 'none' });
      return;
    }
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' });
      return;
    }

    // 校验新密码
    if (!newPassword) {
      wx.showToast({ title: '请输入新密码', icon: 'none' });
      return;
    }
    if (newPassword.length < 6 || newPassword.length > 32) {
      wx.showToast({ title: '密码长度需在6-32位之间', icon: 'none' });
      return;
    }

    this.setData({ resetLoading: true });

    const hashedPassword = sha256(newPassword);

    // 先用 wx.login 获取 code，再用 code 验证身份并重置密码
    wx.login({
      success: (loginRes) => {
        if (!loginRes.code) {
          this.setData({ resetLoading: false });
          wx.showToast({ title: '微信验证失败，请重试', icon: 'none' });
          return;
        }
        api.resetPassword(phone, loginRes.code, hashedPassword)
          .then(() => {
            this.setData({
              resetLoading: false,
              showForgot: false,
              resetPhone: ''
            });
            this._resetPassword = '';
          })
          .catch((err) => {
            this.setData({ resetLoading: false });
            wx.showToast({
              title: (err && err.message) || '重置失败',
              icon: 'none'
            });
          });
      },
      fail: () => {
        this.setData({ resetLoading: false });
        wx.showToast({ title: '微信验证失败，请重试', icon: 'none' });
      }
    });
  }
});
