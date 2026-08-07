const app = getApp();
const api = require('../../utils/api');
const { applyTheme } = require('../../utils/theme');
const auth = require('../../utils/auth');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    avatarUrl: '',
    nickname: '',
    genderIndex: 0,
    genderOptions: ['未知', '男', '女'],
    city: '',
    signature: ''
  },

  onLoad() {
    if (!auth.checkLogin()) return;
    applyTheme(this);
    this.loadUserProfile();
  },

  onShow() {
    applyTheme(this);
  },

  loadUserProfile() {
    const userInfo = getApp().globalData.userInfo || {};
    this.setData({
      avatarUrl: userInfo.avatarUrl || '',
      nickname: userInfo.nickName || '',
      genderIndex: userInfo.gender || 0,
      city: userInfo.city || '',
      signature: userInfo.signature || ''
    });
  },

  chooseAvatar() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const tempFilePath = res.tempFiles[0].tempFilePath;
        this.setData({ avatarUrl: tempFilePath });
        this.uploadAvatar(tempFilePath);
      }
    });
  },

  uploadAvatar(filePath) {
    const { uploadFile } = require('../../utils/request');
    uploadFile({
      url: '/api/v1/users/avatar',
      filePath,
      name: 'file'
    }).then((avatarUrl) => {
      this.setData({ avatarUrl });
    }).catch((err) => {
      console.error('头像上传失败:', err);
      wx.showToast({ title: '上传失败', icon: 'none' });
    });
  },

  onNicknameInput(e) {
    this.setData({ nickname: e.detail.value });
  },

  onGenderChange(e) {
    this.setData({ genderIndex: parseInt(e.detail.value) });
  },

  onCityInput(e) {
    this.setData({ city: e.detail.value });
  },

  onSignatureInput(e) {
    this.setData({ signature: e.detail.value });
  },

  saveProfile() {
    const nickname = this.data.nickname.trim();

    if (!nickname) {
      wx.showToast({ title: '请输入昵称', icon: 'none' });
      return;
    }

    api.updateUserProfile(profileData)
      .then((res) => {
        // request.js 已经处理了 success 判断，成功会直接 resolve data
        // 更新本地存储
        const updatedUserInfo = {
          ...getApp().globalData.userInfo,
          avatarUrl: this.data.avatarUrl,
          nickName: nickname,
          gender: this.data.genderIndex,
          city: this.data.city,
          signature: this.data.signature
        };

        wx.setStorageSync('userInfo', updatedUserInfo);
        getApp().globalData.userInfo = updatedUserInfo;

        wx.navigateBack();
      })
      .catch((err) => {
        console.error('保存用户资料失败:', err);
      });
  }
});
