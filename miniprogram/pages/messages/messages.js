const app = getApp();
const api = require('../../utils/api');
const auth = require('../../utils/auth');
const cache = require('../../utils/cache');
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    isLoggedIn: false,
    loading: false,
    currentTab: 'chat',
    messages: [],
    conversations: [],
    page: 1,
    hasMore: true,
    shareRouteId: '',
    unreadCount: {
      total: 0,
      interaction: 0,
      comment: 0,
      reply: 0,
      like: 0,
      follow: 0,
      system: 0
    },
    imUnreadCount: 0
  },

  onLoad(options) {
    applyTheme(this);
    share.enableShareMenu();
    this.setData({
      shareRouteId: options && options.shareRouteId ? options.shareRouteId : '',
      currentTab: options && options.shareRouteId ? 'chat' : 'chat'
    });
  },

  onShow() {
    applyTheme(this);
    const pendingShareRouteId = app.globalData.pendingShareRouteId || '';
    if (pendingShareRouteId && pendingShareRouteId !== this.data.shareRouteId) {
      app.globalData.pendingShareRouteId = '';
      this.setData({
        shareRouteId: pendingShareRouteId,
        currentTab: 'chat',
        conversations: [],
        messages: []
      });
    }
    this.checkLoginStatus();
  },

  checkLoginStatus() {
    const isLoggedIn = auth.isLoggedIn();
    this.setData({ isLoggedIn });
    if (!isLoggedIn) {
      this.setData({
        messages: [],
        conversations: [],
        unreadCount: { total: 0, interaction: 0, comment: 0, reply: 0, like: 0, follow: 0, system: 0 },
        imUnreadCount: 0
      });
      return;
    }
    // 先展示缓存的会话列表（即时渲染）
    this.loadConversationsFromCache();
    this.loadUnreadCount();
    this.loadCurrentTab();
  },

  loadCurrentTab() {
    if (this.data.currentTab === 'chat' || this.data.shareRouteId) {
      this.loadConversations();
    } else {
      this.loadMessages(true);
    }
  },

  loadUnreadCount() {
    // 先展示缓存的未读数
    const cachedUnread = cache.get('im_unread');
    if (cachedUnread) {
      this.setData({ imUnreadCount: cachedUnread.total || 0 });
    }
    api.message.getUnreadCount()
      .then((data) => this.setData({ unreadCount: data || this.data.unreadCount }))
      .catch(() => {});
    api.im.getImUnreadCount()
      .then((data) => {
        const val = (data && data.total) || 0;
        this.setData({ imUnreadCount: val });
        data && cache.set('im_unread', data);
      })
      .catch(() => {});
  },

  /** 从缓存加载会话列表（即时渲染，无需等待网络） */
  loadConversationsFromCache() {
    const cached = cache.get('im_conversations');
    if (cached && cached.length > 0) {
      this.setData({
        conversations: cached.map(item => ({
          ...item,
          timeText: this.formatTime(item.updatedAt)
        })),
        loading: false
      });
    }
  },

  loadConversations() {
    this.setData({ loading: true });
    api.im.getConversations()
      .then((list) => {
        const formatted = (list || []).map(item => ({
          ...item,
          timeText: this.formatTime(item.updatedAt)
        }));
        this.setData({
          conversations: formatted,
          loading: false
        });
        // 更新缓存（保留原始数据用于下次格式化时间）
        cache.set('im_conversations', list || []);
      })
      .catch((err) => {
        console.error('加载私信失败:', err);
        this.setData({ loading: false });
      });
  },

  loadMessages(reset) {
    if (this.data.loading || (!reset && !this.data.hasMore)) return;
    const nextPage = reset ? 1 : this.data.page;
    this.setData({ loading: true });
    api.message.getMessages(this.data.currentTab, nextPage, 20)
      .then((messages) => {
        const formatted = (messages || []).map(m => ({
          id: m.id,
          userId: m.userId,
          senderId: m.senderId,
          senderName: m.senderName || '系统通知',
          senderAvatar: m.senderAvatar,
          type: m.type,
          content: m.content,
          targetId: m.targetId,
          targetType: m.targetType,
          isRead: m.isRead,
          timeText: this.formatTime(m.createdAt)
        }));
        this.setData({
          messages: reset ? formatted : this.data.messages.concat(formatted),
          page: nextPage,
          hasMore: messages && messages.length === 20,
          loading: false
        });
      })
      .catch((err) => {
        console.error('加载消息失败:', err);
        this.setData({ loading: false });
      });
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.currentTab) return;
    this.setData({
      currentTab: tab,
      page: 1,
      hasMore: true,
      messages: [],
      conversations: []
    });
    this.loadCurrentTab();
  },

  cancelShareRoute() {
    this.setData({ shareRouteId: '' });
    app.globalData.pendingShareRouteId = '';
    this.loadCurrentTab();
  },

  openConversation(e) {
    const targetUserId = e.currentTarget.dataset.userId;
    if (!targetUserId) return;
    if (this.data.shareRouteId) {
      api.im.sendRoute(targetUserId, this.data.shareRouteId)
        .then(() => {
          this.setData({ shareRouteId: '' });
          wx.navigateTo({
            url: '/pages/imchat/imchat?targetUserId=' + targetUserId
          });
        })
        .catch((err) => {
          console.error('发送路线失败:', err);
          wx.showToast({ title: '发送失败', icon: 'none' });
        });
      return;
    }
    wx.navigateTo({
      url: '/pages/imchat/imchat?targetUserId=' + targetUserId
    });
  },

  markAsReadById(id) {
    if (!id) return;
    api.message.markMessageAsRead(id)
      .then(() => {
        this.loadUnreadCount();
      })
      .catch(() => {});
  },

  readAll() {
    wx.showModal({
      title: '确认',
      content: '确定要标记当前消息为已读吗？',
      success: (res) => {
        if (!res.confirm) return;
        api.message.markAllAsRead(this.data.currentTab)
          .then(() => {
            this.loadUnreadCount();
            this.loadMessages(true);
          });
      }
    });
  },

  clearMessages() {
    wx.showModal({
      title: '确认',
      content: '确定要清空当前消息吗？',
      confirmText: '清空',
      confirmColor: '#EF4444',
      success: (res) => {
        if (!res.confirm) return;
        api.message.clearMessages(this.data.currentTab)
          .then(() => {
            this.loadUnreadCount();
            this.setData({ messages: [] });
          });
      }
    });
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    const targetId = e.currentTarget.dataset.targetid;
    const targetType = e.currentTarget.dataset.targettype;
    const senderId = e.currentTarget.dataset.senderId;
    this.markAsReadById(id);

    if (targetType === 'publish' || targetType === 'comment' || targetType === 'reply') {
      wx.navigateTo({ url: '/pages/trip-detail/trip-detail?publishId=' + targetId });
    } else if (targetType === 'user' && targetId) {
      this.openUserProfile(targetId);
    } else if (senderId) {
      this.openUserProfile(senderId);
    }
  },

  openUserProfile(userId) {
    const selfId = wx.getStorageSync('userId') || '';
    if (userId === selfId) {
      wx.switchTab({ url: '/pages/profile/profile' });
      return;
    }
    wx.navigateTo({ url: '/pages/userprofile/userprofile?userId=' + userId });
  },

  formatTime(value) {
    if (!value) return '';
    const date = new Date(String(value).replace(/-/g, '/'));
    if (Number.isNaN(date.getTime())) return '';
    const now = new Date();
    const diff = now - date;
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
    if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
    if (diff < 604800000) return Math.floor(diff / 86400000) + '天前';
    return (date.getMonth() + 1) + '/' + date.getDate();
  },

  onPullDownRefresh() {
    this.setData({ page: 1, hasMore: true });
    this.loadUnreadCount();
    this.loadCurrentTab();
    wx.stopPullDownRefresh();
  },

  onReachBottom() {
    if (this.data.currentTab !== 'chat' && this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 });
      this.loadMessages(false);
    }
  },

  goToLogin() {
    wx.navigateTo({ url: '/pages/login/login' });
  },

  onShareAppMessage() {
    return share.shareToFriend({ title: '拾路派 - 消息中心', path: '/pages/messages/messages' });
  },

  onShareTimeline() {
    return share.shareToTimeline({ title: '拾路派 - 消息中心' });
  }
});
