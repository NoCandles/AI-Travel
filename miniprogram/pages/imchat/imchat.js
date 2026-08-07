const app = getApp();
const api = require('../../utils/api');
const auth = require('../../utils/auth');
const cache = require('../../utils/cache');
const { applyTheme } = require('../../utils/theme');

/** 对方资料缓存 TTL：30 分钟 */
const PROFILE_CACHE_TTL = 30 * 60 * 1000;

function resolveAssetUrl(url) {
  if (!url) return '';
  if (/^(https?:\/\/|cloud:\/\/|wxfile:\/\/|http:\/\/tmp\/)/.test(url)) return url;
  const C = require('../../config/constants');
  return C.CLOUD_BASE_URL + url;
}

/** 获取消息缓存键 */
function msgCacheKey(userId) {
  return 'im_messages_' + userId;
}

/** 获取对方资料缓存键 */
function profileCacheKey(userId) {
  return 'im_profile_' + userId;
}

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    targetUserId: '',
    targetUser: {},
    selfAvatar: '',
    messages: [],
    inputText: '',
    loading: false,
    scrollToId: ''
  },

  onLoad(options) {
    applyTheme(this);
    const targetUserId = options && options.targetUserId ? options.targetUserId : '';
    this.setData({
      targetUserId,
      selfAvatar: this.getSelfAvatar()
    });
    if (!targetUserId) {
      wx.showToast({ title: '用户不存在', icon: 'none' });
      return;
    }
    if (!auth.checkLogin()) return;

    // ① 先加载缓存 — 立即展示
    this.loadFromCache();
    // ② 再静默从服务器拉取最新数据
    this.loadTargetProfile();
    this.loadMessages();
  },

  onShow() {
    applyTheme(this);
  },

  /** 从缓存加载对方资料和消息，立即渲染 */
  loadFromCache() {
    const uid = this.data.targetUserId;

    // 对方资料缓存（30 分钟有效）
    const cachedProfile = cache.get(profileCacheKey(uid), PROFILE_CACHE_TTL);
    if (cachedProfile) {
      this.setData({ targetUser: cachedProfile });
    }

    // 消息缓存
    const cachedMessages = cache.get(msgCacheKey(uid));
    if (cachedMessages && cachedMessages.length > 0) {
      const lastId = cachedMessages[cachedMessages.length - 1].id;
      this.setData({
        messages: cachedMessages,
        scrollToId: 'msg-' + lastId
      });
    }
  },

  getSelfAvatar() {
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo') || {};
    return userInfo.avatarUrl || userInfo.avatar || '';
  },

  loadTargetProfile() {
    const uid = this.data.targetUserId;
    api.getPublicUserProfile(uid)
      .then((data) => {
        const profile = data && data.profile ? data.profile : {};
        const targetUser = {
          id: profile.id,
          nickname: profile.nickname || '旅行家',
          avatar: resolveAssetUrl(profile.avatar),
          signature: profile.signature || ''
        };
        this.setData({ targetUser });
        // 写入缓存
        cache.set(profileCacheKey(uid), targetUser);
      })
      .catch(() => {});
  },

  loadMessages() {
    const uid = this.data.targetUserId;
    const cacheKey = msgCacheKey(uid);
    const oldMessages = this.data.messages;
    const lastLocalId = oldMessages.length > 0 ? oldMessages[oldMessages.length - 1].id : null;

    this.setData({ loading: true });
    api.im.getMessages(uid, 1, 50)
      .then((list) => {
        const messages = (list || []).map(item => ({
          ...item,
          routeCover: resolveAssetUrl(item.routeCover)
        }));

        // 如果服务端数据与缓存最后一条 ID 相同，说明没有新消息，无需更新
        const lastServerId = messages.length > 0 ? messages[messages.length - 1].id : null;
        if (lastLocalId && lastServerId === lastLocalId && messages.length === oldMessages.length) {
          this.setData({ loading: false });
          return;
        }

        this.setData({
          messages,
          loading: false,
          scrollToId: messages.length ? 'msg-' + messages[messages.length - 1].id : ''
        });
        // 写入缓存
        cache.set(cacheKey, messages);
      })
      .catch((err) => {
        console.error('加载聊天失败:', err);
        this.setData({ loading: false });
      });
  },

  onInput(e) {
    this.setData({ inputText: e.detail.value });
  },

  sendText() {
    const text = this.data.inputText.trim();
    if (!text) return;
    this.setData({ inputText: '' });
    const uid = this.data.targetUserId;
    const cacheKey = msgCacheKey(uid);

    api.im.sendText(uid, text)
      .then((msg) => {
        const messages = this.data.messages.concat([msg]);
        this.setData({
          messages,
          scrollToId: 'msg-' + msg.id
        });
        // 更新本地缓存
        cache.set(cacheKey, messages);
      })
      .catch((err) => {
        console.error('发送消息失败:', err);
        wx.showToast({ title: '发送失败', icon: 'none' });
        this.setData({ inputText: text });
      });
  },

  viewRouteDetail(e) {
    const routeId = e.currentTarget.dataset.routeId;
    if (!routeId) return;
    wx.navigateTo({ url: '/pages/trip-detail/trip-detail?publishId=' + routeId });
  },

  viewUserProfile() {
    if (!this.data.targetUserId) return;
    wx.navigateTo({ url: '/pages/userprofile/userprofile?userId=' + this.data.targetUserId });
  }
});
