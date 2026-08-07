/**
 * 拾路派 - AI旅行助手聊天页面 v3.0
 *
 * 状态机：
 *   welcome → params_collected → (editing → params_collected) → confirmed → generating → done
 *
 * 消息类型：
 *   text | typing | param_card | progress | trip_card | hotel_card | map_card
 *
 * 设计风格 (v3.0):
 *   - 白色页面背景 + 项目统一卡片风格（匹配 square / my-trips / profile）
 *   - 扁平简约欢迎区：白色卡片网格
 *   - 参数胶囊式切换
 *   - WebSocket 实时进度推送 + 打字机效果
 */
const app = getApp();
const chatAPI = require('../../api/chat');
const tripAPI = require('../../api/trip');
const hikingAPI = require('../../api/hiking');
const wsManager = require('../../utils/websocket');
const share = require('../../utils/share');
const auth = require('../../utils/auth');
const { applyTheme } = require('../../utils/theme');
const { markdownToNodes } = require('../../utils/markdown');
const { LOCAL_DATA, ROUTE_TITLES } = require('../../utils/local-routes');
const analytics = require('../../utils/analytics');

Page({
  data: {
    messages: [],
    inputText: '',
    scrollToId: '',
    userAvatar: '',
    darkMode: false,
    conversations: [],
    historyVisible: false,
    historyLoading: false,
    currentTitle: '新对话',

    // 欢迎页数据
    quickScenarios: ['周末2日游', '三天小长假', '一日CityWalk', '亲子游', '蜜月旅行', '徒步线路'],
    hotDestinations: [
      { name: '成都', tagline: '美食之都 · 川西秘境', icon: '/images/icons/food.svg', query: '成都周末2日游' },
      { name: '厦门', tagline: '文艺海岛 · 鼓浪屿', icon: '/images/icons/beach.svg', query: '厦门三天小长假' },
      { name: '北京', tagline: '千年古都 · 帝都风华', icon: '/images/icons/landmark.svg', query: '北京周末2日游' },
      { name: '大理', tagline: '风花雪月 · 苍山洱海', icon: '/images/icons/waves.svg', query: '大理三天小长假' },
      { name: '西安', tagline: '十三朝古都 · 碳水天堂', icon: '/images/icons/city-gate.svg', query: '西安周末2日游' },
      { name: '杭州', tagline: '人间天堂 · 西湖断桥', icon: '/images/icons/flower.svg', query: '杭州周末2日游' }
    ],

    // 路线卡片状态
    routeTabActive: 'seasonal',

    // 对话状态
    phase: 'welcome',        // welcome | params_collected | editing | confirmed | generating | done
    tripParams: null,
    pendingSpotInput: '',
    paramPayload: null,
    tempStartDate: '',      // 🚀 临时存储日期选择器的值
    // 🆕 UX: 网络状态、滚动到底部、快捷标签、路线工作台
    _offline: false,
    _showScrollBottom: false,
    quickChips: [],
    _routeData: null,
    _currentRoute: null,
    _routeCollapsed: false,

    // 🚀 新增：WebSocket 连接状态
    _conversationId: null,   // 对话ID（用于 WebSocket 连接）
    _wsConnected: false,     // WebSocket 是否已连接
    // 🆕 流式打字：当前正在流式输出的消息 ID
    _streamingMsgId: null,
    _streamBuffer: '',
    _streamTimer: null
  },

  _msgIdCounter: 0,
  _progressTimer: null,
  _typewriterTimer: null,
  _snapshotTimer: null,
  _suspendSnapshot: false,
  _sending: false, // 🚀 F2: 发送防抖标志
  _listViewHeight: 0, // 🆕 UX: scroll-view 可视高度缓存

  onLoad() {
    applyTheme(this);
    this.refreshUserAvatar();
    this.showWelcome();

    // 🚀 生成 conversationId 并连接 WebSocket（用于接收 AI 优化参数）
    if (!this.data._conversationId) {
      this.data._conversationId = this._generateUUID();

      // 连接聊天 WebSocket（异步，不阻塞页面加载）
      this._connectChatWebSocket(this.data._conversationId);
    }
    this.loadConversations(true);

    // 🚀 监听键盘高度变化，防止输入框被遮挡
    wx.onKeyboardHeightChange((res) => {
      const keyboardHeight = res.height;

      if (keyboardHeight > 0) {
        // 键盘弹起，滚动到输入框位置
        this.scrollToInput(keyboardHeight);
      }
    });

    // 启用分享
    share.enableShareMenu();
  },

  /**
   * 滚动到输入框位置（防止键盘遮挡）
   */
  scrollToInput(keyboardHeight) {
    const query = wx.createSelectorQuery().in(this);
    query.select('.input-bar').boundingClientRect();
    query.exec((res) => {
      if (res && res[0]) {
        const inputBarTop = res[0].top;
        const windowHeight = wx.getWindowInfo().windowHeight;
        const scrollTop = inputBarTop + keyboardHeight - windowHeight;
        
        if (scrollTop > 0) {
          wx.pageScrollTo({
            scrollTop: scrollTop,
            duration: 300
          });
        }
      }
    });
  },

  onShow() {
    applyTheme(this);
    this.refreshUserAvatar();
  },

  onUnload() {
    if (this._progressTimer) clearTimeout(this._progressTimer);
    if (this._typewriterTimer) clearTimeout(this._typewriterTimer);
    if (this._snapshotTimer) clearTimeout(this._snapshotTimer);
    if (this._streamTimer) clearTimeout(this._streamTimer);
    this.saveCurrentSnapshot();
    wsManager.disconnect();
  },

  getUserAvatarFromProfile() {
    const globalUserInfo = (app.globalData && app.globalData.userInfo) || {};
    const storageUserInfo = wx.getStorageSync('userInfo') || {};
    const userInfo = {
      ...storageUserInfo,
      ...globalUserInfo
    };

    return userInfo.avatarUrl
      || userInfo.avatar
      || userInfo.headImgUrl
      || userInfo.headimgurl
      || userInfo.photo
      || '';
  },

  refreshUserAvatar() {
    const userAvatar = this.getUserAvatarFromProfile();
    if (userAvatar !== this.data.userAvatar) {
      this.setData({ userAvatar });
    }
  },

  // ═══════════════════════════════════════
  // 会话历史
  // ═══════════════════════════════════════

  loadConversations(autoOpenLatest = false) {
    if (!wx.getStorageSync('userId') || !wx.getStorageSync('token')) return;
    this.setData({ historyLoading: true });
    chatAPI.listConversations().then(list => {
      const conversations = (list || []).map(item => ({
        ...item,
        timeText: this.formatConversationTime(item.updatedAt || item.createdAt),
        countText: item.messageCount ? item.messageCount + '条' : '空对话'
      }));
      this.setData({ conversations, historyLoading: false });
      if (autoOpenLatest && conversations.length > 0) {
        const latest = conversations[0];
        if (latest.id && latest.id !== this.data._conversationId) {
          this.switchConversationById(latest.id, true);
        }
      }
    }).catch(() => {
      this.setData({ historyLoading: false });
    });
  },

  formatConversationTime(value) {
    if (!value) return '';
    const date = new Date(String(value).replace(/-/g, '/'));
    if (Number.isNaN(date.getTime())) return '';
    const now = new Date();
    const sameDay = date.toDateString() === now.toDateString();
    const pad = n => String(n).padStart(2, '0');
    if (sameDay) return pad(date.getHours()) + ':' + pad(date.getMinutes());
    return (date.getMonth() + 1) + '月' + date.getDate() + '日';
  },

  toggleHistory() {
    const nextVisible = !this.data.historyVisible;
    this.setData({ historyVisible: nextVisible });
    if (nextVisible) {
      this.loadConversations(false);
    }
  },

  closeHistory() {
    this.setData({ historyVisible: false });
  },

  onNewConversation() {
    this.saveCurrentSnapshot();
    if (this._progressTimer) clearTimeout(this._progressTimer);
    if (this._typewriterTimer) clearTimeout(this._typewriterTimer);
    if (this._streamTimer) clearTimeout(this._streamTimer);
    const conversationId = this._generateUUID();
    this._suspendSnapshot = true;
    this.setData({
      _conversationId: conversationId,
      currentTitle: '新对话',
      historyVisible: false,
      _currentTripId: null,
      _progressMsgId: null,
      _tripRendered: false,
      // 🆕 路线工作台：清空
      _routeData: null,
      _currentRoute: null,
      _routeCollapsed: false
    });
    this.showWelcome();
    this._suspendSnapshot = false;
    this._connectChatWebSocket(conversationId);
  },

  onSwitchConversation(e) {
    const id = e.currentTarget.dataset.id;
    this.switchConversationById(id, false);
  },

  switchConversationById(conversationId, silent) {
    if (!conversationId || conversationId === this.data._conversationId) {
      this.setData({ historyVisible: false });
      return;
    }
    this.saveCurrentSnapshot();
    this.setData({ historyLoading: true });
    chatAPI.getHistory(conversationId).then(history => {
      const messages = ((history && history.messages) || []).map((item, index) => ({
        id: item.id || ('his_' + index),
        role: item.role,
        type: item.type || 'text',
        content: item.content || '',
        payload: item.payload || null,
        timestamp: item.timestamp || Date.now()
      }));
      this._suspendSnapshot = true;
      if (messages.length === 0) {
        this.setData({
          _conversationId: conversationId,
          currentTitle: (history && history.conversation && history.conversation.title) || '新对话',
          historyVisible: false,
          historyLoading: false
        });
        this.showWelcome();
      } else {
        this.setData({
          _conversationId: conversationId,
          currentTitle: (history && history.conversation && history.conversation.title) || this.buildConversationTitle(messages),
          messages,
          phase: this.inferPhaseFromMessages(messages),
          scrollToId: messages[messages.length - 1].id,
          quickChips: [],
          historyVisible: false,
          historyLoading: false
        });
        this.restoreRuntimeFromMessages(messages);
      }
      this._suspendSnapshot = false;
      this._connectChatWebSocket(conversationId);
      if (!silent) wx.showToast({ title: '已切换对话', icon: 'none' });
    }).catch(() => {
      this.setData({ historyLoading: false });
    });
  },

  onDeleteConversation(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '删除聊天记录',
      content: '删除后此对话不会再出现在历史列表中，确认删除吗？',
      confirmText: '删除',
      confirmColor: '#EF4444',
      success: (res) => {
        if (!res.confirm) return;
        chatAPI.deleteConversation(id).then(() => {
          const isCurrent = id === this.data._conversationId;
          this.loadConversations(false);
          if (isCurrent) this.onNewConversation();
        });
      }
    });
  },

  inferPhaseFromMessages(messages) {
    if (!messages || messages.length === 0) return 'welcome';
    if (messages.some(m => m.type === 'progress')) return 'generating';
    if (messages.some(m => m.type === 'trip_card' || m.type === 'map_card')) return 'done';
    if (messages.some(m => m.type === 'param_card')) return 'params_collected';
    return 'welcome';
  },

  restoreRuntimeFromMessages(messages) {
    const paramMessage = [...messages].reverse().find(m => m.type === 'param_card' && m.payload);
    if (paramMessage) {
      this.data.tripParams = {
        destination: paramMessage.payload.destination,
        startPoint: paramMessage.payload.startPoint || '',
        endPoint: paramMessage.payload.endPoint || '',
        startPointOptions: paramMessage.payload.startPointOptions || [],
        endPointOptions: paramMessage.payload.endPointOptions || [],
        days: paramMessage.payload.days,
        startDate: paramMessage.payload.startDate,
        endDate: paramMessage.payload.endDate,
        preference: paramMessage.payload.preference,
        budget: paramMessage.payload.budget,
        travelMode: paramMessage.payload.travelMode || 'drive',
        travelModeLabel: paramMessage.payload.travelModeLabel || '自驾',
        mustVisit: paramMessage.payload.mustVisit || []
      };
      this.data.paramPayload = paramMessage.payload;
    }
    // 🆕 路线工作台：从历史消息恢复路线数据
    const tripMessage = [...messages].reverse().find(m => m.type === 'trip_card' && m.payload);
    if (tripMessage && tripMessage.payload.allRoutes) {
      const routes = tripMessage.payload.allRoutes;
      this.data._routeData = routes;
      this.setData({
        _routeData: routes,
        _currentRoute: routes.seasonal || routes.trendy || routes.classic,
        routeTabActive: 'seasonal',
        _routeCollapsed: false
      });
    }
    const mapMessage = [...messages].reverse().find(m => m.type === 'map_card' && m.payload);
    if (mapMessage && mapMessage.payload.tripId) {
      this.data._currentTripId = mapMessage.payload.tripId;
      this.setData({ _currentTripId: mapMessage.payload.tripId });
    }
  },

  buildConversationTitle(messages) {
    const firstUser = (messages || []).find(m => m.role === 'user' && m.content);
    if (!firstUser) return '新对话';
    return firstUser.content.length > 16 ? firstUser.content.slice(0, 15) + '…' : firstUser.content;
  },

  scheduleSnapshot() {
    if (this._suspendSnapshot || !this.data._conversationId) return;
    if (this._snapshotTimer) clearTimeout(this._snapshotTimer);
    this._snapshotTimer = setTimeout(() => this.saveCurrentSnapshot(), 600);
  },

  saveCurrentSnapshot() {
    if (this._suspendSnapshot || !this.data._conversationId) return;
    const messages = (this.data.messages || []).filter(m => m.id !== '_welcome' && m.type !== 'typing');
    if (messages.length === 0) return;
    const title = this.buildConversationTitle(messages);
    this.setData({ currentTitle: title });
    chatAPI.saveSnapshot(this.data._conversationId, {
      title,
      phase: this.data.phase,
      messages: messages.map(m => ({
        id: m.id,
        role: m.role,
        type: m.type,
        content: m.content || '',
        payload: m.payload || null,
        timestamp: m.timestamp || Date.now()
      }))
    }).then(() => this.loadConversations(false)).catch(() => {});
  },

  // ═══════════════════════════════════════
  // 消息管理
  // ═══════════════════════════════════════

  _nextId() {
    return 'msg_' + (++this._msgIdCounter);
  },

  /**
   * 🚀 生成 UUID（用于 conversationId）
   */
  _generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  },

  addMessage(msg) {
    msg.id = msg.id || this._nextId();
    msg.timestamp = Date.now();

    // ── 消息连续性标记 ──
    const messages = this.data.messages;
    if (messages.length > 0) {
      const last = messages[messages.length - 1];
      if (last.role === msg.role && last.type === msg.type && (Date.now() - last.timestamp) < 30000) {
        msg._cont = true;
      }
    }

    // 🆕 UX: 时间分隔线 — 不同天/不同小时的消息间插入
    const timeDivider = this._insertTimeDivider(messages, msg);
    if (timeDivider) {
      // 先加分隔线，再加消息
      const dividerIdx = messages.length;
      this.setData({
        [`messages[${dividerIdx}]`]: { id: this._nextId(), _timeDivider: timeDivider }
      });
    }

    // 消息上限
    const MAX = 500;
    const KEEP = 200;
    if (messages.length >= MAX - 1) {
      messages.splice(0, MAX - KEEP);
    }

    const idx = messages.length;
    this.setData({
      [`messages[${idx}]`]: msg,
      scrollToId: msg.id
    });
    this.scheduleSnapshot();
    return msg.id;
  },

  /**
   * 🆕 UX: 判断是否需要插入时间分隔线
   * 返回 "今天"/"昨天"/"日期" 或 null
   */
  _insertTimeDivider(messages, newMsg) {
    if (messages.length === 0 && newMsg.role === 'system') return null;
    const prevMsg = messages.length > 0 ? messages[messages.length - 1] : null;
    const prevTime = prevMsg ? (prevMsg.timestamp || 0) : 0;
    const currTime = newMsg.timestamp || Date.now();
    if (!prevTime) return null;

    const diff = currTime - prevTime;
    // 超过 5 分钟插入时间分隔线
    if (diff < 300000) return null;

    const fmt = (ts) => {
      const d = new Date(ts);
      const now = new Date();
      const isToday = d.toDateString() === now.toDateString();
      const yesterday = new Date(now);
      yesterday.setDate(yesterday.getDate() - 1);
      const isYesterday = d.toDateString() === yesterday.toDateString();
      if (isToday) return '今天';
      if (isYesterday) return '昨天';
      return (d.getMonth() + 1) + '月' + d.getDate() + '日';
    };
    return fmt(currTime);
  },

  addUserMessage(text) {
    this.addMessage({ role: 'user', type: 'text', content: text });
  },

  addAIMessage(content, type = 'text', payload = null) {
    // 🚀 F1: 如果是文本消息且含 Markdown 标记，预渲染为 rich-text nodes
    const msg = {
      role: 'assistant',
      type: type,
      content: content,
      payload: payload
    };
    if ((type === 'text' || type === 'markdown') && content) {
      const hasMarkdown = /\*\*|\*[^*]+\.\*|`[^`]+`|^[-*+]\s|^\d+\.\s/.test(content);
      if (hasMarkdown || type === 'markdown') {
        msg._richNodes = markdownToNodes(content);
      }
    }
    return this.addMessage(msg);
  },

  addTyping(callback, delay = 50) {
    const msgId = this.addMessage({ role: 'assistant', type: 'typing', content: '' });
    if (callback) {
      setTimeout(() => {
        try {
          const ret = callback();
          // callback 返回 Promise（异步操作）时，typing 保持显示直到 Promise 完成
          if (ret && typeof ret.then === 'function') {
            ret.finally(() => this.removeMessage(msgId));
          } else {
            // 同步 callback：立即移除 typing
            this.removeMessage(msgId);
          }
        } catch (e) {
          this.removeMessage(msgId);
        }
      }, delay);
    }
    return msgId;
  },

  removeMessage(msgId) {
    const messages = this.data.messages.filter(m => m.id !== msgId);
    // 删除消息后，scrollToId 指向剩余最后一条，避免 scroll-into-view 失效回弹到顶部
    const last = messages.length > 0 ? messages[messages.length - 1] : null;
    this.setData({
      messages,
      scrollToId: last ? last.id : ''
    });
    this.scheduleSnapshot();
  },

  // ═══════════════════════════════════════
  // 欢迎消息
  // ═══════════════════════════════════════

  showWelcome() {
    this.setData({
      messages: [{ role: 'system', type: 'text', content: '嘿，想去哪？', id: '_welcome' }],
      phase: 'welcome',
      tripParams: null,
      paramPayload: null,
      routeTabActive: 'seasonal',
      quickChips: [],
      // 🆕 路线工作台：清空
      _routeData: null,
      _currentRoute: null,
      _routeCollapsed: false
    });
  },

  // ═══════════════════════════════════════
  // 输入处理
  // ═══════════════════════════════════════

  onInput(e) {
    this.setData({ inputText: e.detail.value });
  },

  onSend() {
    // 🚀 F2: 防抖
    if (this._sending) return;
    const text = this.data.inputText.trim();
    if (!text) return;
    this._sending = true;

    // 🆕 UX: 发送震动反馈
    wx.vibrateShort({ type: 'light' }).catch(() => {});

    this.addUserMessage(text);
    this.setData({ inputText: '', quickChips: [] });

    // 立即显示思考状态
    const thinkingId = this.addMessage({ role: 'assistant', type: 'typing', content: '' });

    let actionPromise = null;
    if (this.data.phase === 'welcome' || this.data.phase === 'editing') {
      actionPromise = this.handleUserIntent(text);
    } else if (this.data.phase === 'params_collected') {
      actionPromise = this.handleExtraInfo(text);
    } else if (this.data.phase === 'done') {
      actionPromise = this.handleDoneAction(text);
    }

    const releaseSend = () => { this._sending = false; };
    const removeThinking = () => {
      const messages = this.data.messages.filter(m => m.id !== thinkingId);
      this.setData({ messages });
      releaseSend();
    };
    if (actionPromise && typeof actionPromise.then === 'function') {
      actionPromise.finally(removeThinking);
    } else {
      removeThinking();
    }
  },

  /**
   * 行程生成完成后（phase === 'done'）的输入处理
   * 策略：统一走后端 AI 意图识别；仅纯前端动作（查看行程/分享/打包）保留快速通道省打字。
   * 「换一个/还是原来的好/不想去XX」等多轮指代全走后端，不前端硬匹配。
   */
  handleDoneAction(text) {
    const t = text.trim();

    if (t.includes('重新生成') || t.includes('再试一次')) {
      return this.retryCurrentGeneration();
    }

    // 纯前端动作：跳页（同步，立刻返回）
    if (t.includes('我的行程') || t.includes('查看行程') || t.includes('查看我的')) {
      wx.switchTab({ url: '/pages/my-trips/my-trips' });
      return null;
    }
    // 打包清单（同步开新页）
    if (t.includes('打包') || t.includes('行李清单')) {
      this._openPackingList();
      return null;
    }
    // 分享提示（同步加消息）
    if (t === '分享' || t === '分享给朋友' || t.includes('分享给')) {
      this.addAIMessage('点击右上角「···」选择「转发给朋友」即可分享这条路线~');
      return null;
    }

    // 其余全部交后端 AI：返回 Promise，让 onSend 控制 thinking 状态
    return this.callAPIForDoneAction(text);
  },

  // 找最近一条 action_card（用于"继续"等对话确认，现多由后端处理，保留兜底）
  _findLastPendingAction() {
    const msgs = this.data.messages || [];
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].type === 'action_card' && msgs[i].payload) return msgs[i].payload;
    }
    return null;
  },

  // phase=done 时调后端 AI 解析意图
  callAPIForDoneAction(text) {
    return chatAPI.send(text, this.data._conversationId, this.data._currentTripId).then(res => {
      this.data._conversationId = res.conversationId;
      const intent = res.intent;
      const modifyTrip = res.modifyTrip;
      if (intent === 'modify_trip' && modifyTrip) {
        return this._handleModifyTrip(modifyTrip, res);
      }
      // 其他意图（chitchat/unclear/new_plan 等）：直接显示 AI 回复
      const replyMsgs = (res.messages || []).filter(m => m.type === 'text' || m.type === 'markdown');
      if (replyMsgs.length > 0) {
        replyMsgs.forEach(m => this.addAIMessage(m.content, m.type));
      } else {
        this.addAIMessage('你可以告诉我：换掉某个景点、重新规划第X天、查看我的行程、生成打包清单、重新规划~');
      }
      return null;
    }).catch(() => {
      this.addAIMessage('网络开了点小差，再试一次吧~');
    });
  },

  // 处理修改行程意图（后端已执行可执行的 action，前端按 actionType 分发 + 回显）
  _handleModifyTrip(modifyTrip, res) {
    const actionType = modifyTrip.actionType;
    const tripId = this.data._currentTripId;
    // 先显示 AI 回复（后端执行成功后已用结果覆盖 reply，如"帮你换成新都桥观景台~"）
    const replyMsgs = (res.messages || []).filter(m => m.type === 'text' || m.type === 'markdown');
    if (replyMsgs.length > 0) {
      replyMsgs.forEach(m => this.addAIMessage(m.content, m.type));
    }

    if (!tripId) {
      this.addAIMessage('当前没有可修改的行程，先生成一条路线吧~');
      return null;
    }

    // replace_spot_done：后端已替换单景点 → 刷新 trip_card，等待刷新完成
    if (actionType === 'replace_spot_done') {
      const tip = modifyTrip.replacement ? '已换成「' + modifyTrip.replacement + '」~' : '景点已替换~';
      return this._refreshTripCardAfterChange(tripId, tip, ['换一个', '还是原来的好', '查看我的行程']);
    }
    // regenerate_day：直接调用重新生成接口，返回 Promise 以保持 thinking 状态
    if (actionType === 'regenerate_day' && modifyTrip.modifyDayNum) {
      return this._regenerateDay(tripId, modifyTrip.modifyDayNum, modifyTrip.modifyTarget || '');
    }
    // swap_another_done：后端已重新替换 → 刷新
    if (actionType === 'swap_another_done') {
      const tip = modifyTrip.replacement ? '已换成「' + modifyTrip.replacement + '」~' : '已换一个~';
      return this._refreshTripCardAfterChange(tripId, tip, ['换一个', '还是原来的好', '查看我的行程']);
    }
    // undo：已撤销 → 刷新
    if (actionType === 'undo') {
      return this._refreshTripCardAfterChange(tripId, '已恢复原来的景点~', ['查看我的行程']);
    }
    // confirm_proposal：确认上轮提议，不做额外操作
    if (actionType === 'confirm_proposal') {
      this.setData({ quickChips: ['查看我的行程'] });
      return null;
    }
    // needs_clarify：后端无法定位，引导用户说清楚
    if (actionType === 'needs_clarify' || modifyTrip.needsClarify) {
      this.setData({ quickChips: ['重新规划第1天', '重新规划第2天', '查看我的行程'] });
      return null;
    }
    this.setData({ quickChips: ['换一个', '还是原来的好', '查看我的行程'] });
    return null;
  },

  // 前端直接调 replaceSpot（多轮"换一个"等已由后端执行，这里仅作手动兜底入口）
  _replaceSpot(tripId, spotId, hint) {
    return tripAPI.replaceSpot(tripId, spotId, hint).then(() => {
      return this._refreshTripCardAfterChange(tripId, '已帮你换掉~', ['换一个', '还是原来的好', '查看我的行程']);
    }).catch(() => {
      this.addAIMessage('换个景点失败，稍后重试或换个说法~');
    });
  },

  // 调用后端重新生成某一天的行程（返回 Promise，调用方负责 thinking 状态）
  _regenerateDay(tripId, dayNum, target) {
    return tripAPI.regenerateDay(tripId, dayNum).then(() => {
      const tip = target ? '已根据你的反馈重新规划第' + dayNum + '天行程~' : '第' + dayNum + '天行程已重新生成~';
      return this._refreshTripCardAfterChange(tripId, tip);
    }).catch(() => {
      this.addAIMessage('重新生成失败，请稍后重试，或去行程详情页手动编辑~');
    });
  },

  // 行程变更后重新拉取详情并刷新对话内的 trip_card（返回 Promise，等待数据刷新完成）
  _refreshTripCardAfterChange(tripId, tipText, quickChips) {
    const chips = quickChips || ['查看我的行程', '重新规划'];
    return tripAPI.getTripDetail(tripId).then(trip => {
      const routes = this._convertBackendRoutes(trip);
      this.data._routeData = routes;
      const routeId = this.data.routeTabActive || 'seasonal';
      const currentRoute = routes[routeId] || routes.seasonal || routes.trendy || routes.classic;
      // 🆕 路线工作台：更新 panel 数据，不再操作 messages
      this.setData({
        _routeData: routes,
        _currentRoute: currentRoute
      });
      if (tipText) {
        this.addAIMessage(tipText + '想再调可以继续告诉我，比如「重新规划第' + (this.data.tripParams ? this.data.tripParams.days : 1) + '天」~');
      }
      this.setData({ quickChips: chips });
    }).catch(() => {
      this.addAIMessage(tipText);
      this.setData({ quickChips: chips });
    });
  },

  // 打开打包清单
  _openPackingList() {
    const tripId = this.data._currentTripId;
    if (!tripId) {
      this.addTyping(() => {
        this.addAIMessage('请先生成并保存路线，再查看打包清单~');
      });
      return;
    }
    const dest = this.data.tripParams ? this.data.tripParams.destination : '';
    wx.navigateTo({
      url: '/pages/packing-list/packing-list?' +
        'tripId=' + encodeURIComponent(tripId) +
        '&destination=' + encodeURIComponent(dest) +
        '&canEdit=1'
    });
  },

  onQuickChip(e) {
    const text = e.currentTarget.dataset.text;
    this.setData({ inputText: text });
    this.onSend();
  },

  // 🆕 UX: 长按消息复制
  onLongPressMsg(e) {
    const content = e.currentTarget.dataset.content;
    if (!content) return;
    wx.showActionSheet({
      itemList: ['复制文本'],
      success: (res) => {
        if (res.tapIndex === 0) {
          wx.setClipboardData({
            data: content,
            success: () => wx.showToast({ title: '已复制', icon: 'none' })
          });
        }
      }
    });
  },

  // 🆕 UX: 滚动到底部
  scrollToBottom() {
    const msgs = this.data.messages || [];
    const last = msgs.length > 0 ? msgs[msgs.length - 1] : null;
    if (last) {
      this.setData({
        scrollToId: last.id,
        _showScrollBottom: false
      });
    }
  },

  // 🆕 UX: 监听消息列表滚动 — 显示/隐藏"滚动到底部"FAB
  onMsgListScroll(e) {
    const { scrollTop, scrollHeight } = e.detail;
    if (!scrollHeight) return;

    // 缓存 scroll-view 可视高度（只需测量一次）
    if (!this._listViewHeight) {
      const query = wx.createSelectorQuery().in(this);
      query.select('.msg-list').boundingClientRect();
      query.exec((res) => {
        if (res && res[0]) {
          this._listViewHeight = res[0].height;
        }
      });
      this._listViewHeight = this._listViewHeight || (wx.getWindowInfo().windowHeight - 180);
    }

    // 到底部附近（100rpx 内）就隐藏 FAB，否则显示
    const atBottom = (scrollTop + this._listViewHeight + 100) >= scrollHeight;
    this.setData({ _showScrollBottom: !atBottom });
  },

  // ═══════════════════════════════════════
  // 🆕 流式打字效果
  // ═══════════════════════════════════════

  /**
   * 处理流式 token：批量合并后更新消息内容
   */
  _handleStreamToken(text) {
    // 如果还没有流式消息，创建一条
    if (!this.data._streamingMsgId) {
      const msgId = this.addMessage({
        role: 'assistant',
        type: 'text',
        content: '',
        _streaming: true
      });
      this.data._streamingMsgId = msgId;
      this.data._streamBuffer = '';
    }

    this.data._streamBuffer += text;

    // 节流：每 60ms 或积累超过 8 个字刷新一次
    if (this._streamTimer) return;
    this._streamTimer = setTimeout(() => {
      this._streamTimer = null;
      const buffer = this.data._streamBuffer;
      if (!buffer || !this.data._streamingMsgId) return;
      // 保留 buffer 用于后续追加
      this.updateMessageContent(this.data._streamingMsgId, buffer + '▎');
    }, 60);
  },

  /**
   * 流式输出完成：移除光标，保存最终文本
   */
  _handleStreamDone(data) {
    if (this._streamTimer) {
      clearTimeout(this._streamTimer);
      this._streamTimer = null;
    }
    if (!this.data._streamingMsgId) return;
    const finalText = data && data.fullText ? data.fullText : this.data._streamBuffer;
    this.updateMessageContent(this.data._streamingMsgId, finalText);
    const idx = (this.data.messages || []).findIndex(m => m.id === this.data._streamingMsgId);
    if (idx >= 0) {
      this.setData({
        [`messages[${idx}]._streaming`]: false
      });
    }
    this.data._streamingMsgId = null;
    this.data._streamBuffer = '';
  },

  // 🆕 用户点击正在流式输出的消息 → 立即显示完整内容
  onSkipStreaming() {
    if (!this.data._streamingMsgId) return;
    if (this._streamTimer) {
      clearTimeout(this._streamTimer);
      this._streamTimer = null;
    }
    const fullContent = this.data._streamBuffer;
    this.updateMessageContent(this.data._streamingMsgId, fullContent);
    const idx = (this.data.messages || []).findIndex(m => m.id === this.data._streamingMsgId);
    if (idx >= 0) {
      this.setData({
        [`messages[${idx}]._streaming`]: false
      });
    }
    this.data._streamingMsgId = null;
    this.data._streamBuffer = '';
  },

  // ═══════════════════════════════════════
  // NLP 意图解析
  // ═══════════════════════════════════════

  handleUserIntent(text) {
    return this.callAPIForIntent(text);
  },

  callAPIForIntent(text) {
    return chatAPI.send(text, this.data._conversationId, this.data._currentTripId).then(res => {
      this.data._conversationId = res.conversationId;
      if (res.params) {
        const prevStartPoint = (this.data.tripParams && this.data.tripParams.startPoint) || '';
        const prevEndPoint = (this.data.tripParams && this.data.tripParams.endPoint) || '';
        this.data.tripParams = {
          destination: res.params.destination,
          startPoint: res.params.startPoint || prevStartPoint,
          endPoint: res.params.endPoint || prevEndPoint,
          startPointOptions: res.params.startPointOptions || [],
          endPointOptions: res.params.endPointOptions || [],
          days: res.params.days,
          startDate: res.params.startDate,
          endDate: res.params.endDate,
          preference: res.params.preference,
          budget: res.params.budget,
          travelMode: res.params.travelMode || 'drive',
          travelModeLabel: res.params.travelModeLabel || '自驾',
          mustVisit: res.params.mustVisit || [],
          hikingProfile: res.params.hikingProfile || null
        };
        this.data.phase = 'params_collected';
        if (res.messages && res.messages.length > 0) {
          res.messages.forEach(m => {
            if (m.type === 'text') this.addAIMessage(m.content);
            if (m.type === 'param_card') this.showParamCard(this.data.tripParams, false);
          });
        }
      } else {
        if (res.messages && res.messages.length > 0) {
          res.messages.forEach(m => {
            if (m.type === 'text') this.addAIMessage(m.content);
          });
        }
      }
    }).catch(() => {
      const params = this.parseNaturalLanguage(text);
      this.data.tripParams = params;
      this.data.phase = 'params_collected';
      this.showParamCard(params, false);
    });
  },

  handleExtraInfo(text) {
    return this.handleUserIntent(text);
  },

  // [已废弃] 前端正则猜"是否改参数"——现在统一走后端意图识别，保留仅作历史参考。
  // 删除前端硬匹配是"对话化"改造的核心：意图和说法解耦，不该前端枚举关键词。
  looksLikeParamChange(text) {
    return false;
  },

  /**
   * 🚀 F5: 前端 NLP 兜底（极简版 — 后端不可达时的最低保底）
   * 后端已有 DeepSeek NLP + fallbackParse 完整实现，前端只需生成可用的默认参数。
   * 删除了与前端的 CITIES/KNOWN_SPOTS 匹配（后端已覆盖）。
   */
  parseNaturalLanguage(text) {
    const params = {
      destination: '',
      startPoint: '',
      endPoint: '',
      days: 2,
      startDate: '',
      endDate: '',
      preference: '轻松休闲',
      budget: '中等预算',
      travelMode: 'drive',
      travelModeLabel: '自驾',
      mustVisit: []
    };

    // 天数解析
    const dayMatch = text.match(/(\d+)\s*[天日]/);
    if (dayMatch) {
      params.days = Math.min(Math.max(parseInt(dayMatch[1]), 1), 15);
    } else if (text.includes('周末')) { params.days = 2; }
    else if (text.includes('小长假')) { params.days = 3; }
    else if (text.includes('一周') || text.includes('七日')) { params.days = 7; }

    // 日期
    const now = new Date();
    const end = new Date(now);
    end.setDate(end.getDate() + params.days - 1);
    const pad = n => String(n).padStart(2, '0');
    params.startDate = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
    params.endDate = end.getFullYear() + '-' + pad(end.getMonth() + 1) + '-' + pad(end.getDate());

    if (text.includes('轻松') || text.includes('休闲')) params.preference = '轻松休闲';
    else if (text.includes('网红') || text.includes('打卡')) params.preference = '网红打卡';
    else if (text.includes('小众') || text.includes('探索')) params.preference = '小众探索';
    else if (text.includes('美食') || text.includes('吃')) params.preference = '美食之旅';
    else if (text.includes('文化') || text.includes('历史')) params.preference = '文化体验';

    if (text.includes('穷游') || text.includes('省钱')) params.budget = '经济实惠';
    else if (text.includes('高端') || text.includes('豪华')) params.budget = '高端享受';

    if (text.includes('徒步')) { params.travelMode = 'hiking'; params.travelModeLabel = '徒步'; }
    else if (text.includes('公交') || text.includes('地铁')) { params.travelMode = 'transit'; params.travelModeLabel = '公交'; }

    return params;
  },

  // ═══════════════════════════════════════
  // 参数卡片
  // ═══════════════════════════════════════

  showParamCard(params, editing) {
    // 🆕 UX: 从 editing 切回展示模式时加过渡动效
    const wasEditing = this.data.paramPayload && this.data.paramPayload.editing === true;
    const isEditing = editing === true;

    const payload = {
      editing: isEditing,
      _editAnim: wasEditing && !isEditing, // 编辑→展示 过渡
      destination: params.destination,
      startPoint: params.startPoint || '',
      endPoint: params.endPoint || '',
      startPointOptions: params.startPointOptions || [],
      endPointOptions: params.endPointOptions || [],
      days: params.days,
      startDate: params.startDate,
      endDate: params.endDate,
      dateDisplay: this.formatDateRange(params.startDate, params.endDate),
      preference: params.preference,
      budget: params.budget,
      travelMode: params.travelMode,
      travelModeLabel: params.travelModeLabel,
      mustVisit: [...(params.mustVisit || [])],
      hikingProfile: params.hikingProfile || null
    };
    this.data.paramPayload = payload;

    // 根据起终点状态生成不同的提示文案
    let msgText;
    if (editing) {
      msgText = '可以点击参数切换，或修改目的地：';
    } else if (params.startPointOptions && params.startPointOptions.length > 0 && !params.startPoint) {
      msgText = '已识别到出发城市，请点击下方📍选择具体出发地点，再确认生成路线：';
    } else {
      msgText = '确认一下你的旅行参数';
    }
    this.addAIMessage(msgText, 'param_card', payload);
    
    // 🚀 初始化日期选择器的初始值
    if (params.startDate) {
      this.setData({ tempStartDate: params.startDate });
    }
  },

  // ISO 日期转友好展示：2026-07-02,2026-07-08 → 7月2日-7月8日
  formatDateRange(startISO, endISO) {
    const fmt = (iso) => {
      if (!iso) return '';
      const parts = String(iso).split('-');
      if (parts.length !== 3) return iso;
      return parseInt(parts[1]) + '月' + parseInt(parts[2]) + '日';
    };
    return fmt(startISO) + '-' + fmt(endISO);
  },

  onParamInput(e) {
    const field = e.currentTarget.dataset.field;
    const value = e.detail.value;
    if (field === 'destination') {
      this.data.tripParams.destination = value;
      this.data.paramPayload.destination = value;
    } else if (field === 'startPoint') {
      this.data.tripParams.startPoint = value;
      this.data.paramPayload.startPoint = value;
    } else if (field === 'endPoint') {
      this.data.tripParams.endPoint = value;
      this.data.paramPayload.endPoint = value;
    } else if (field === 'newSpot') {
      this.setData({ pendingSpotInput: value });
    }
  },

  // 📍 选择起点位置（微信式发送定位）
  onChooseStartLocation() {
    this._chooseLocationWithAuth('start');
  },

  // 📍 选择终点位置（微信式发送定位）
  onChooseEndLocation() {
    this._chooseLocationWithAuth('end');
  },

  // 带权限预检查的位置选择
  _chooseLocationWithAuth(type) {
    // 先检查是否已有位置授权
    wx.getSetting({
      success: (settingRes) => {
        const hasAuth = settingRes.authSetting && settingRes.authSetting['scope.userLocation'];
        if (hasAuth) {
          this._chooseLocation(type);
        } else {
          // 未授权，先主动请求授权
          wx.authorize({
            scope: 'scope.userLocation',
            success: () => this._chooseLocation(type),
            fail: () => {
              // 用户拒绝授权，引导去设置
              wx.showModal({
                title: '需要位置权限',
                content: '选择出发/返程地点需要开启位置权限，去设置开启？',
                confirmText: '去设置',
                confirmColor: '#3B82F6',
                success: (r) => {
                  if (r.confirm) {
                    wx.openSetting({
                      success: (openRes) => {
                        // 用户从设置返回后，如果已授权，直接调起选择
                        if (openRes.authSetting && openRes.authSetting['scope.userLocation']) {
                          this._chooseLocation(type);
                        }
                      }
                    });
                  }
                }
              });
            }
          });
        }
      },
      fail: () => {
        // getSetting 失败，直接尝试调起
        this._chooseLocation(type);
      }
    });
  },

  // 通用：调起微信位置选择
  _chooseLocation(type) {
    wx.chooseLocation({
      success: (res) => {
        // res: { name, address, latitude, longitude }
        const locName = res.name || res.address || '我的位置';
        const locData = {
          name: locName,
          address: res.address || '',
          latitude: res.latitude,
          longitude: res.longitude
        };
        if (type === 'start') {
          this.data.tripParams.startPoint = locName;
          this.data.tripParams.startPointLocation = locData;
          this.data.paramPayload.startPoint = locName;
          // 往返路线：终点为空或与旧起点一致时，自动同步为新起点
          if (!this.data.tripParams.endPoint ||
              this.data.tripParams.endPoint === this.data.paramPayload.endPoint) {
            this.data.tripParams.endPoint = locName;
            this.data.tripParams.endPointLocation = locData;
            this.data.paramPayload.endPoint = locName;
          }
        } else {
          this.data.tripParams.endPoint = locName;
          this.data.tripParams.endPointLocation = locData;
          this.data.paramPayload.endPoint = locName;
        }
        this._refreshParamCardPayload();
        wx.vibrateShort({ type: 'light' });
      },
      fail: (err) => {
        console.warn('chooseLocation fail:', err);
        // 用户取消不算错误
        if (err && err.errMsg && err.errMsg.indexOf('cancel') === -1) {
          wx.showToast({ title: '获取位置失败，请重试', icon: 'none' });
        }
      }
    });
  },

  // 快捷操作：使用起点作为终点（往返路线）
  onUseStartAsEnd() {
    const sp = this.data.tripParams.startPoint || '';
    if (!sp) {
      wx.showToast({ title: '请先选择起点', icon: 'none' });
      return;
    }
    this.data.tripParams.endPoint = sp;
    this.data.tripParams.endPointLocation = this.data.tripParams.startPointLocation;
    this.data.paramPayload.endPoint = sp;
    this._refreshParamCardPayload();
    wx.showToast({ title: '已设为往返', icon: 'none' });
  },

  // 刷新当前参数卡片消息的 payload（起点/终点变化时调用）
  _refreshParamCardPayload() {
    const messages = this.data.messages;
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i].type === 'param_card' && messages[i].payload) {
        messages[i].payload.startPoint = this.data.paramPayload.startPoint;
        messages[i].payload.endPoint = this.data.paramPayload.endPoint;
        break;
      }
    }
    this.setData({ messages });
  },

  onEditParams() {
    this.data.phase = 'editing';
    const messages = this.data.messages.slice(0, -1);
    this.setData({ messages });
    this.showParamCard(this.data.tripParams, true);
  },

  onCancelEdit() {
    this.data.phase = 'params_collected';
    const messages = this.data.messages.slice(0, -1);
    this.setData({ messages });
  },

  onCycleDays() {
    if (this.data.phase !== 'editing') return;
    const days = [1, 2, 3, 4, 5, 7];
    const idx = days.indexOf(this.data.tripParams.days);
    const newDays = days[(idx + 1) % days.length];
    this.data.tripParams.days = newDays;
    const now = new Date();
    const end = new Date(now);
    end.setDate(end.getDate() + newDays - 1);
    const pad = n => String(n).padStart(2, '0');
    this.data.tripParams.startDate = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
    this.data.tripParams.endDate = end.getFullYear() + '-' + pad(end.getMonth() + 1) + '-' + pad(end.getDate());
    this.data.paramPayload.days = newDays;
    this.data.paramPayload.startDate = this.data.tripParams.startDate;
    this.data.paramPayload.endDate = this.data.tripParams.endDate;
    this.data.paramPayload.dateDisplay = this.formatDateRange(this.data.tripParams.startDate, this.data.tripParams.endDate);
    this.refreshParamCard();
  },

  onCyclePreference() {
    if (this.data.phase !== 'editing') return;
    const prefs = ['轻松休闲', '网红打卡', '小众探索', '文化体验', '美食之旅', '户外冒险'];
    const idx = prefs.indexOf(this.data.tripParams.preference);
    this.data.tripParams.preference = prefs[(idx + 1) % prefs.length];
    this.data.paramPayload.preference = this.data.tripParams.preference;
    this.refreshParamCard();
  },

  // 🚀 新增：处理起始日期选择
  onStartDateChange(e) {
    if (this.data.phase !== 'editing') return;
    const startDate = e.detail.value;  // 格式：yyyy-MM-dd
    const days = this.data.tripParams.days || 3;
    
    // 计算结束日期
    const start = new Date(startDate.replace(/-/g, '/'));
    const end = new Date(start);
    end.setDate(end.getDate() + days - 1);
    const pad = n => String(n).padStart(2, '0');
    const endDate = end.getFullYear() + '-' + pad(end.getMonth() + 1) + '-' + pad(end.getDate());
    
    // 更新参数
    this.data.tripParams.startDate = startDate;
    this.data.tripParams.endDate = endDate;
    this.data.paramPayload.startDate = startDate;
    this.data.paramPayload.endDate = endDate;
    this.data.paramPayload.dateDisplay = this.formatDateRange(startDate, endDate);
    
    // 更新 tempStartDate（用于 picker 的 value）
    this.setData({ tempStartDate: startDate });
    
    this.refreshParamCard();
  },

  onCycleBudget() {
    if (this.data.phase !== 'editing') return;
    const budgets = ['经济实惠', '中等预算', '高端享受'];
    const idx = budgets.indexOf(this.data.tripParams.budget);
    this.data.tripParams.budget = budgets[(idx + 1) % budgets.length];
    this.data.paramPayload.budget = this.data.tripParams.budget;
    this.refreshParamCard();
  },

  onCycleMode() {
    if (this.data.phase !== 'editing') return;
    const modes = [
      { value: 'drive', label: '自驾' },
      { value: 'transit', label: '公交' },
      { value: 'hiking', label: '徒步' }
    ];
    const idx = modes.findIndex(m => m.value === this.data.tripParams.travelMode);
    const next = modes[(idx + 1) % modes.length];
    this.data.tripParams.travelMode = next.value;
    this.data.tripParams.travelModeLabel = next.label;
    this.data.paramPayload.travelMode = next.value;
    this.data.paramPayload.travelModeLabel = next.label;
    this.refreshParamCard();
  },

  onAddSpot(e) {
    const spot = (e && e.detail && e.detail.value) ? e.detail.value.trim() : this.data.pendingSpotInput.trim();
    if (!spot) return;
    if (!this.data.tripParams.mustVisit) this.data.tripParams.mustVisit = [];
    if (!this.data.tripParams.mustVisit.includes(spot)) {
      this.data.tripParams.mustVisit.push(spot);
      this.data.paramPayload.mustVisit = [...this.data.tripParams.mustVisit];
    }
    this.setData({ pendingSpotInput: '' });
    this.refreshParamCard();
  },

  onRemoveSpot(e) {
    const spot = e.currentTarget.dataset.spot;
    const idx = this.data.tripParams.mustVisit.indexOf(spot);
    if (idx !== -1) {
      this.data.tripParams.mustVisit.splice(idx, 1);
      this.data.paramPayload.mustVisit = [...this.data.tripParams.mustVisit];
      this.refreshParamCard();
    }
  },

  refreshParamCard() {
    const messages = this.data.messages.slice(0, -1);
    this.setData({ messages }, () => {
      this.showParamCard(this.data.tripParams, this.data.phase === 'editing');
    });
  },

  onSaveEditParams() {
    this.data.phase = 'params_collected';
    const messages = this.data.messages.slice(0, -1);
    this.setData({ messages });
    this.addUserMessage('修改好了，就这样吧');
    this.addTyping(() => {
      this.showParamCard(this.data.tripParams, false);
    });
  },

  // ═══════════════════════════════════════
  // 确认生成 — 对接后端 API
  // ═══════════════════════════════════════

  onConfirmParams() {
    if (!auth.checkLogin()) return;
    const params = this.data.tripParams;
    // 起终点校验：识别到出发城市时，用户必须发送起点定位
    if (params && params.startPointOptions && params.startPointOptions.length > 0 && !params.startPoint) {
      wx.showToast({ title: '请发送起点定位', icon: 'none' });
      return;
    }
    if (params && params.startPoint && !params.endPoint &&
        params.endPointOptions && params.endPointOptions.length > 0) {
      wx.showToast({ title: '请发送终点定位', icon: 'none' });
      return;
    }
    this.data.phase = 'confirmed';
    const messages = this.data.messages.slice(0, -1);
    this.setData({ messages });
    this.addUserMessage('确认生成');

    // 云托管模式和本地调试模式都调用后端 API
    this._callBackendConfirm(params);
  },

  _callBackendConfirm(params) {
    // 🚀 重置渲染标志，防止重复渲染
    this.data._tripRendered = false;

    analytics.track('trip_generation_started', {
      destination: params.destination,
      days: params.days,
      hasMustVisit: !!(params.mustVisit && params.mustVisit.length)
    });
    chatAPI.confirm(this.data._conversationId, {
      destination: params.destination,
      startPoint: params.startPoint || '',
      endPoint: params.endPoint || '',
      days: params.days,
      startDate: params.startDate,
      endDate: params.endDate,
      preference: params.preference,
      budget: params.budget,
      travelMode: params.travelMode,
      mustVisit: params.mustVisit,
      hikingProfile: params.hikingProfile || null
    }).then(res => {
      const tripId = typeof res === 'string' ? res : (res && res.tripId);
      if (tripId) {
        this.setData({ _currentTripId: tripId, _tripSaved: false });
        this.data._currentTripId = tripId;
        this._showProgressUI();

        // ── WebSocket 连接，替代 HTTP 轮询 ──
        this._connectWebSocket(tripId);
      } else {
        console.warn('confirmAndGenerate returned no tripId, falling back');
        analytics.track('trip_generation_local_fallback', { reason: 'missing_trip_id' });
        this._startLocalSimulation();
      }
    }).catch(err => {
      const errMsg = (err && err.message) || '生成失败';
      console.warn('Backend confirm failed:', errMsg);
      // 积分不足等业务错误，在对话中显示提示
      if (errMsg.includes('积分') || errMsg.includes('余额')) {
        this.addBotMessage(errMsg);
      } else {
        analytics.track('trip_generation_failed', { reason: errMsg });
        wx.showToast({ title: errMsg, icon: 'none' });
      }
    });
  },

  /**
   * 建立 WebSocket 连接，接收进度推送
   */
  _connectWebSocket(tripId) {
    const self = this;

    wsManager.connect(tripId, {
      onConnected(data) {
        // 🆕 UX: 连接恢复，清除离线状态
        self.setData({ _offline: false });
      },

      onProgress(data) {
        // WS 推送进度更新
        // data: { stage: "start"|"ai_done"|"routeA", message: "..." }
        if (!self.data._progressMsgId) return;

        const stageMap = {
          'start':     { percent: 10, activeStep: 0 },
          'ai_done':   { percent: 65, activeStep: 2 },
          'routeA':    { percent: 85, activeStep: 3 },
          'packing':   { percent: 95, activeStep: 4 }
        };

        const info = stageMap[data.stage] || {};
        const pct = info.percent || Math.min(60, 10 + ((data.stage || '').length * 5));

        const stepLabels = self._getProgressStepLabels();
        const steps = stepLabels.map((_, i) => {
          if (i < (info.activeStep || 0)) return 'done';
          if (i === (info.activeStep || 0)) return 'active';
          return '';
        });

        self.updateMessagePayload(self.data._progressMsgId, {
          percent: pct,
          currentLabel: data.message || '生成中...',
          steps: steps
        });
      },

      onDone(data) {
        // 🚀 防止重复渲染：如果已经渲染过，直接返回
        if (self.data._tripRendered) {
          return;
        }
        
        // 生成完成！获取最终行程数据
        self.data._tripRendered = true;  // 🚀 标记已渲染
        self.removeMessage(self.data._progressMsgId);
        self.data.phase = 'done';
        self.data._progressMsgId = null;

        // 获取完整行程数据
        tripAPI.getTripDetail(tripId).then(trip => {
          if (trip && trip.travelMode === 'hiking') {
            self._openGeneratedHikingRoute(trip);
            analytics.track('trip_generation_succeeded', { tripId, source: 'websocket_hiking' });
            return;
          }
          setTimeout(() => {
            self._renderTripFromBackend(trip);
            setTimeout(() => self._renderHotelFromBackend(trip), 500);
            self._renderMapCard();
          }, 400);
          analytics.track('trip_generation_succeeded', { tripId, source: 'websocket' });
        }).catch(() => {
          self.addAIMessage('路线已生成！请前往「我的行程」查看详情。');
        });
      },

      onError(message) {
        console.error('[Chat] WS error:', message);
        // 🆕 UX: 更新离线状态
        self.setData({ _offline: true });
        // WS 不可用 → 降级为 HTTP 轮询
        self._pollTripStatus(self.data._currentTripId, 0);
      },

      onClose(code, reason) {
        // 仅当未完成生成时标记离线（done 后主动关闭不应误报）
        if (self.data.phase !== 'done') {
          self.setData({ _offline: true });
        }
        // 如果还在 generating 阶段意外断开，降级为轮询
        if (self.data.phase === 'generating' && self.data._currentTripId) {
          console.warn('[Chat] WS disconnected during generation, falling back to polling');
          self._pollTripStatus(self.data._currentTripId, 0);
        }
      }
    });
  },

  /**
   * 🚀 连接聊天 WebSocket，接收 AI 优化参数推送
   * @param {string} conversationId - 对话ID
   */
  _connectChatWebSocket(conversationId) {
    const self = this;
    // 云托管对扁平 WebSocket 路径的转发已验证可用；通过 query 标识聊天通道。
    const wsPath = `/ws/chat/${conversationId}?channel=conversation`;


    wsManager.connect(conversationId, {
      onConnected(data) {
        self.data._wsConnected = true;
        self.setData({ _offline: false });
      },

      onProgress(data) {
        // 聊天 WebSocket 不接收进度推送，只接收参数优化
      },

      // 🚀 新增：处理 AI 优化参数推送
      onParamOptimized(data) {

        // 更新参数卡片
        if (data && data.params) {
          self.data.tripParams = {
            destination: data.params.destination,
            days: data.params.days,
            startDate: data.params.startDate,
            endDate: data.params.endDate,
            preference: data.params.preference,
            budget: data.params.budget,
            travelMode: data.params.travelMode || 'drive',
            travelModeLabel: data.params.travelModeLabel || '自驾',
            mustVisit: data.params.mustVisit || [],
            hikingProfile: data.params.hikingProfile || null
          };

          // 更新 UI（如果当前显示的是参数卡片）
          if (self.data.phase === 'params_collected' || self.data.phase === 'editing') {
            self.showParamCard(self.data.tripParams, self.data.phase === 'editing');
          }

          // 显示优化提示
          if (data.text) {
            self.addAIMessage(data.text, 'text');
          }
        }

        self.data._wsConnected = true;
      },

      // 🆕 流式打字：处理 DeepSeek token 推送
      onToken(data) {
        if (!data || !data.text) return;
        self._handleStreamToken(data.text);
      },

      // 🆕 流式打字：处理完成
      onDone(data) {
        self._handleStreamDone(data);
      },

      onDone(data) {
      },

      onError(message) {
        console.error('[Chat] 聊天 WS 错误:', message);
        self.data._wsConnected = false;
        // 不重试，降级为同步响应（规则解析结果已返回）
      },

      onClose(code, reason) {
        self.data._wsConnected = false;
      }
    }, wsPath);  // 🚀 传入自定义路径
  },

  _pollTripStatus(tripId, attempts) {
    const maxAttempts = 30;
    const baseInterval = 2000;
    const maxInterval = 10000;

    // 🚀 防止重复渲染：如果已经渲染过，直接返回
    if (this.data._tripRendered) {
      return;
    }

    if (attempts >= maxAttempts) {
      this._handleGenerationTimeout(tripId);
      return;
    }

    tripAPI.getTripDetail(tripId).then(trip => {
      // 🚀 再次检查，防止在请求过程中状态变化
      if (this.data._tripRendered) {
        return;
      }

      this._updateProgressFromStatus(trip.status, attempts);

      if (trip.status === 'DRAFT' || trip.status === 'SAVED') {
        this.data._tripRendered = true;  // 🚀 标记已渲染
        this.removeMessage(this.data._progressMsgId);
        this.data.phase = 'done';
        setTimeout(() => {
          this._renderTripFromBackend(trip);
          setTimeout(() => this._renderHotelFromBackend(trip), 500);
          this._renderMapCard();
        }, 400);
        analytics.track('trip_generation_succeeded', { tripId, source: 'polling', attempts });
      } else if (trip.status === 'FAILED') {
        this.removeMessage(this.data._progressMsgId);
        this.addAIMessage('抱歉，路线生成失败了。请重试或修改参数。');
        this.data.phase = 'done';
        this.setData({ quickChips: ['重新生成', '修改参数'] });
        analytics.track('trip_generation_failed', { tripId, reason: 'backend_failed' });
      } else {
        const nextInterval = Math.min(baseInterval * Math.pow(1.5, attempts), maxInterval);
        setTimeout(() => this._pollTripStatus(tripId, attempts + 1), nextInterval);
      }
    }).catch(() => {
      const nextInterval = Math.min(baseInterval * Math.pow(1.5, attempts), maxInterval);
      setTimeout(() => this._pollTripStatus(tripId, attempts + 1), nextInterval);
    });
  },

  /** AI 徒步生成完成后，进入独立路线页读取真实路线、路段和安全数据。 */
  _openGeneratedHikingRoute(trip) {
    hikingAPI.getRoutesByTrip(trip.id).then((routes) => {
      if (!routes || !routes.length) {
        this.addAIMessage('路线已生成，徒步详情仍在整理中，请稍后从“我的行程”进入查看。');
        return;
      }
      // 生成完成即保存到“我的行程”；失败不拦截查看路线，出发时会再次确保状态正确。
      tripAPI.saveTrip(trip.id).catch(() => null).then(() => {
        this.addAIMessage('三条徒步方案已生成。你可以在路线页切换方案，查看真实分段、爬升和安全提醒。');
        wx.navigateTo({ url: '/pages/hiking-route/hiking-route?tripId=' + encodeURIComponent(trip.id) });
      });
    }).catch(() => {
      this.addAIMessage('路线已生成！请前往“我的行程”查看徒步方案。');
    });
  },

  /** 生成长时间未完成时，优先展示后端已产生的草稿，而非留给用户空白页。 */
  _handleGenerationTimeout(tripId) {
    this.removeMessage(this.data._progressMsgId);
    this.data._progressMsgId = null;
    this.data.phase = 'done';
    analytics.track('trip_generation_timeout', { tripId });

    tripAPI.getTripDetail(tripId).then((trip) => {
      if (trip && trip.days && trip.days.length) {
        this.data._tripRendered = true;
        this._renderTripFromBackend(trip);
        this._renderHotelFromBackend(trip);
        this._renderMapCard();
        this.addAIMessage('生成时间比预期更长，先为你展示当前草稿；你可以继续编辑或重新生成。');
        this.setData({ quickChips: ['查看路线', '重新生成', '修改参数'] });
        analytics.track('trip_generation_draft_shown', { tripId });
        return;
      }
      this.addAIMessage('生成时间较长，暂未得到可用草稿。你可以重新生成，或先修改参数再试。');
      this.setData({ quickChips: ['重新生成', '修改参数'] });
    }).catch(() => {
      this.addAIMessage('生成时间较长，暂未得到可用草稿。你可以重新生成，或先修改参数再试。');
      this.setData({ quickChips: ['重新生成', '修改参数'] });
    });
  },

  /** 对当前行程发起重试，并复用统一进度和超时兜底流程。 */
  retryCurrentGeneration() {
    const tripId = this.data._currentTripId;
    if (!tripId) {
      this.addAIMessage('请先确认生成参数后再试一次。');
      return Promise.resolve();
    }
    this.data._tripRendered = false;
    this._showProgressUI();
    analytics.track('trip_generation_retried', { tripId });
    return tripAPI.regenerateTrip(tripId)
      .then(() => this._pollTripStatus(tripId, 0))
      .catch(() => {
        this.removeMessage(this.data._progressMsgId);
        this.data._progressMsgId = null;
        this.data.phase = 'done';
        this.addAIMessage('重新生成失败，请检查网络后再试。');
        analytics.track('trip_generation_retry_failed', { tripId });
      });
  },

  _showProgressUI() {
    this.data.phase = 'generating';

    const stepLabels = [
      '分析你的\n需求',
      '看看当地\n天气☀️',
      '小派正在\n规划路线…',
      '整理多个\n方案',
      '匹配推荐\n住宿'
    ];

    const progressPayload = {
      percent: 5,
      currentLabel: '小派正在帮你规划路线…',
      steps: ['active', '', '', '', ''],
      stepLabels: stepLabels
    };

    this.data._progressMsgId = this.addAIMessage('', 'progress', progressPayload);
  },

  _updateProgressFromStatus(status, attempts) {
    if (!this.data._progressMsgId) return;

    const progressMap = {
      'GENERATING': { percent: Math.min(20 + attempts * 5, 90), label: '小派正在规划路线…', activeStep: 2 },
      'DRAFT': { percent: 95, label: '路线规划完成🎉', activeStep: 3 }
    };

    const info = progressMap[status] || { percent: Math.min(20 + attempts * 5, 90), label: '小派正在规划路线…', activeStep: 2 };

    const stepLabels = this._getProgressStepLabels();
    const steps = stepLabels.map((_, i) => {
      if (i < info.activeStep) return 'done';
      if (i === info.activeStep) return 'active';
      return '';
    });

    this.updateMessagePayload(this.data._progressMsgId, {
      percent: info.percent,
      currentLabel: info.label,
      steps: steps
    });
  },

  _getProgressStepLabels() {
    return ['分析你的\n需求', '看看当地\n天气☀️', '小派正在\n规划路线…', '整理多个\n方案', '匹配推荐\n住宿'];
  },

  /**
   * 打字机效果 — 逐字渲染 AI 流式输出
   * @param {string} msgId - 消息ID（已创建的空文本消息）
   * @param {string} fullText - 完整文本
   * @param {number} speed - 每字间隔(ms)，默认 50
   */
  _startTypewriter(msgId, fullText, speed = 50) {
    if (!fullText || !msgId) return;

    let index = 0;
    const chars = [...fullText]; // 支持 emoji 等多字节字符
    const self = this;

    const typeNext = () => {
      if (index >= chars.length) {
        self._typewriterTimer = null;
        return;
      }

      const partial = chars.slice(0, index + 1).join('');
      self.updateMessageContent(msgId, partial);
      index++;

      self._typewriterTimer = setTimeout(typeNext, speed);
    };

    typeNext();
  },

  /**
   * 🚀 F3: 使用索引路径定向更新消息文本内容（避免全量遍历）
   */
  updateMessageContent(msgId, content) {
    const idx = (this.data.messages || []).findIndex(m => m.id === msgId);
    if (idx < 0) return;
    this.setData({
      [`messages[${idx}].content`]: content
    });
    this.scheduleSnapshot();
  },

  _renderTripFromBackend(trip) {
    const routes = this._convertBackendRoutes(trip);
    this.data._routeData = routes;
    this.data.routeTabActive = 'seasonal';
    this.setData({
      _routeData: routes,
      _currentRoute: routes.seasonal || routes.trendy || routes.classic,
      routeTabActive: 'seasonal',
      _routeCollapsed: false
    });
    analytics.track('ai_route_explanations_shown', {
      tripId: trip.id || this.data._currentTripId || '',
      routeCount: Object.keys(routes).length
    });
    // 存 trip_card 到消息列表（用于历史恢复），标记 _hidden 避免 UI 渲染
    this.addMessage({
      role: 'assistant',
      type: 'trip_card',
      content: '',
      payload: { allRoutes: routes, currentRoute: routes.seasonal || routes.trendy || routes.classic },
      _hidden: true
    });
    this.addAIMessage('✅ 路线已生成！路线详情在下方工作台中查看~', 'text');
  },

  _convertBackendRoutes(trip) {
    const routes = {};
    const titles = {
      seasonal: '当月时令定制版',
      trendy: '当下网红爆款版',
      classic: '经典稳妥版'
    };

    if (trip.routes && trip.routes.length > 0) {
      trip.routes.forEach(r => {
        const routeId = r.id || 'seasonal';
        const days = (r.days || []).map(d => {
          const points = d.points || d.spots || [];
          return {
            date: d.date || '',
            spots: points.map(s => s.name || s.spotName || '').join(' → ') || d.notes || '',
            spotDetails: points.map((s, index) => ({
              name: s.name || s.spotName || '',
              arrivalTime: s.arrivalTime || '',
              travelGuide: s.travelGuide || '',
              reason: this.getSpotReason(s, index, points.length),
              address: s.address || '',
              latitude: s.latitude || 0,
              longitude: s.longitude || 0
            }))
          };
        });

        const spotCount = (r.days || []).reduce((sum, d) => sum + ((d.points || d.spots || []).length), 0);
        const distance = this.formatRouteDistance(r, trip);

        routes[routeId] = {
          distance: distance,
          spotCount: spotCount,
          title: r.title || titles[routeId] || '备选路线',
          days: days
        };
      });
    }

    if (Object.keys(routes).length === 0 && trip.days) {
      const days = trip.days.map(d => {
        const points = d.points || d.spots || [];
        return {
          date: '第' + d.day + '天',
          spots: points.map(s => s.name || s.spotName || '').join(' → '),
          spotDetails: points.map((s, index) => ({
            name: s.name || s.spotName || '',
            arrivalTime: s.arrivalTime || '',
            travelGuide: s.travelGuide || '',
            reason: this.getSpotReason(s, index, points.length),
            address: s.address || '',
            latitude: s.latitude || 0,
            longitude: s.longitude || 0
          }))
        };
      });
      routes.seasonal = {
        distance: this.formatRouteDistance({ days: trip.days }, trip),
        spotCount: days.reduce((sum, d) => sum + (d.spots ? d.spots.split('→').length : 0), 0),
        title: '当月时令定制版',
        days: days
      };
    }

    return routes;
  },

  getSpotReason(spot, index, total) {
    const backendReason = spot.recommendReason || spot.reason || spot.arrangeReason || '';
    if (backendReason) return backendReason;
    if (index === 0) return '优先安排为当天首站，兼顾出发后的通达性。';
    if (index === total - 1) return '放在当天后段，便于顺路收尾并减少折返。';
    return '与前后景点顺路衔接，尽量减少往返时间。';
  },

  formatRouteDistance(route, trip) {
    // 优先用实际坐标估算里程（保证三条路线计算方式一致，避免AI返回值偏差大）
    // 注意：estimateRouteDistance 返回的是公里（km）
    const estimated = this.estimateRouteDistance(route);
    if (estimated > 0) {
      return estimated.toFixed(0) + 'km';
    }
    // 估算失败时（无坐标），回退到 AI 返回值
    const raw = route && (route.totalDistance || route.distance || route.distanceMeters || route.distanceKm);
    if (typeof raw === 'string' && raw.trim()) return raw.includes('km') ? raw : raw + 'km';
    if (typeof raw === 'number' && raw > 0) return raw > 500 ? (raw / 1000).toFixed(0) + 'km' : raw.toFixed(0) + 'km';
    if (trip && typeof trip.totalDistance === 'number' && trip.totalDistance > 0) {
      return (trip.totalDistance / 1000).toFixed(0) + 'km';
    }
    return '路线规划中';
  },

  estimateRouteDistance(route) {
    const points = [];
    (route && route.days || []).forEach(day => {
      const dayPoints = day.points || day.spots || [];
      dayPoints.forEach(point => {
        const lat = Number(point.latitude);
        const lng = Number(point.longitude);
        if (lat && lng) points.push({ lat, lng });
      });
    });
    if (points.length < 2) return 0;
    let total = 0;
    for (let i = 1; i < points.length; i++) {
      total += this.getPointDistance(points[i - 1], points[i]);
    }
    return total;
  },

  getPointDistance(a, b) {
    const toRad = n => n * Math.PI / 180;
    const R = 6371;
    const dLat = toRad(b.lat - a.lat);
    const dLng = toRad(b.lng - a.lng);
    const lat1 = toRad(a.lat);
    const lat2 = toRad(b.lat);
    const h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
  },

  _renderHotelFromBackend(trip) {
    const hotels = [];
    
    if (trip.days) {
      trip.days.forEach(d => {
        if (d.hotel) {
          const h = d.hotel;
          const hotelName = h.name || h.hotelName || '';
          
          // 🚀 检测是否是一日游/无住宿的情况
          const isNoAccommodation = !hotelName || 
              hotelName.includes('无住宿') || 
              hotelName.includes('不需要') || 
              hotelName.includes('无需') ||
              hotelName.includes('一日游');
          
          if (isNoAccommodation) {
            // 一日游情况，只显示提示文字，不显示价格和评分
            hotels.push({
              name: hotelName || '一日游无需住宿',
              meta: '当天往返',
              price: '',
              isDayTrip: true
            });
          } else {
            hotels.push({
              name: hotelName || '酒店',
              meta: (h.address || '') + (h.rating ? ' · 评分' + h.rating : ''),
              price: h.price ? '¥' + h.price : '',
              isDayTrip: false
            });
          }
        }
      });
    }
    
    if (hotels.length === 0 && trip.hotels) {
      trip.hotels.forEach(h => {
        const hotelName = h.name || h.hotelName || '';
        const isNoAccommodation = !hotelName || 
            hotelName.includes('无住宿') || 
            hotelName.includes('不需要') || 
            hotelName.includes('无需') ||
            hotelName.includes('一日游');
        
        if (isNoAccommodation) {
          hotels.push({
            name: hotelName || '一日游无需住宿',
            meta: '当天返回',
            price: '',
            isDayTrip: true
          });
        } else {
          hotels.push({
            name: hotelName || '酒店',
            meta: (h.address || '') + (h.rating ? ' · 评分' + h.rating : ''),
            price: h.price ? '¥' + h.price : '',
            isDayTrip: false
          });
        }
      });
    }
    
    // 🚀 如果没有有效酒店，不显示酒店卡片
    if (hotels.length === 0) {
      return;
    }

    this.addAIMessage('', 'hotel_card', {
      destination: trip.destination || this.data.tripParams.destination,
      hotels: hotels.slice(0, 3)
    });
  },

  _renderMapCard() {
    this.addAIMessage('', 'map_card', {
      destination: this.data.tripParams.destination,
      tripId: this.data._currentTripId
    });
  },

  // ═══════════════════════════════════════
  // 本地模拟生成（后端不可用时降级）
  // ═══════════════════════════════════════

  _startLocalSimulation() {
    this.data.phase = 'generating';

    const stepLabels = this._getProgressStepLabels();

    const progressPayload = {
      percent: 5,
      currentLabel: '小派正在规划路线（离线模式）…',
      steps: ['active', '', '', '', ''],
      stepLabels: stepLabels
    };

    const progressMsgId = this.addAIMessage('', 'progress', progressPayload);

    const stages = [
      { pct: 20, stepIdx: 0, label: '分析你的需求', delay: 800 },
      { pct: 35, stepIdx: 1, label: '看看当地天气☀️', delay: 1000 },
      { pct: 60, stepIdx: 2, label: '小派正在规划路线…', delay: 1800 },
      { pct: 85, stepIdx: 3, label: '整理多个方案…', delay: 1400 },
      { pct: 95, stepIdx: 4, label: '匹配推荐住宿…', delay: 1000 }
    ];

    let step = 0;
    const runStep = () => {
      if (step >= stages.length) {
        this.removeMessage(progressMsgId);
        this.data.phase = 'done';
        setTimeout(() => this._showLocalTripCard(), 400);
        return;
      }
      const s = stages[step];
      const stepStates = progressPayload.steps.map((_, i) => {
        if (i < s.stepIdx) return 'done';
        if (i === s.stepIdx) return 'active';
        return '';
      });
      this.updateMessagePayload(progressMsgId, { percent: s.pct, currentLabel: s.label, steps: stepStates });
      step++;
      this._progressTimer = setTimeout(runStep, s.delay);
    };
    this._progressTimer = setTimeout(runStep, 500);
  },

  // ═══════════════════════════════════════
  // 路线卡片
  // ═══════════════════════════════════════

  _showLocalTripCard() {
    const dest = this.data.tripParams.destination;
    const routes = this._generateLocalRoutes(dest);
    this.data._routeData = routes;
    this.data.routeTabActive = 'seasonal';
    this.setData({
      _routeData: routes,
      _currentRoute: routes.seasonal,
      routeTabActive: 'seasonal',
      _routeCollapsed: false
    });
    // 存 trip_card 用于历史恢复
    this.addMessage({
      role: 'assistant',
      type: 'trip_card',
      content: '',
      payload: { allRoutes: routes, currentRoute: routes.seasonal },
      _hidden: true
    });
    this.addAIMessage('✅ 路线已生成！（离线模式）路线详情在下方工作台中查看~', 'text');
    setTimeout(() => this._showLocalHotelCard(dest), 500);
  },

  toggleRoutePanel() {
    this.setData({ _routeCollapsed: !this.data._routeCollapsed });
  },

  onSwitchRoute(e) {
    const route = e.currentTarget.dataset.route;
    this.data.routeTabActive = route;
    const routes = this.data._routeData;
    this.setData({
      routeTabActive: route,
      _currentRoute: routes ? routes[route] : null
    });
  },

  /**
   * 🚀 F6: 从 data/local-routes.js 加载本地模拟路线（后端不可用时降级）
   */
  _generateLocalRoutes(dest) {
    const withTitle = {};
    const db = LOCAL_DATA.routes;
    const data = db[dest] || db['成都'];
    for (const key of ['seasonal', 'trendy', 'classic']) {
      if (data[key]) {
        withTitle[key] = { ...data[key], title: ROUTE_TITLES[key] };
      }
    }
    return withTitle;
  },

  /**
   * 🚀 F6: 从 data/local-routes.js 加载本地模拟酒店
   */
  _showLocalHotelCard(dest) {
    const hotels = LOCAL_DATA.hotels[dest] || LOCAL_DATA.hotels['成都'];
    this.addAIMessage('', 'hotel_card', { destination: dest, hotels });
    setTimeout(() => this._renderMapCard(), 600);
  },

  /**
   * 🚀 F3: 使用索引路径定向更新消息 payload（避免全量遍历）
   */
  updateMessagePayload(msgId, updates) {
    const idx = (this.data.messages || []).findIndex(m => m.id === msgId);
    if (idx < 0) return;
    const msg = this.data.messages[idx];
    const mergedPayload = { ...msg.payload, ...updates };
    this.setData({
      [`messages[${idx}].payload`]: mergedPayload
    });
    this.scheduleSnapshot();
  },

  // ═══════════════════════════════════════
  // 底部操作
  // ═══════════════════════════════════════

  onSaveTrip() {
    const tripId = this.data._currentTripId;
    if (tripId) {
      tripAPI.saveTrip(tripId).then(() => {
        app.globalData.tripListDirty = true;
        app.globalData.squareDirty = true;
        this.setData({ _tripSaved: true });
        this._showSavedMessage();
      }).catch(() => {
        this._showSavedMessage();
      });
    } else {
      this._showSavedMessage();
    }
  },

  _showSavedMessage() {
    this.addUserMessage('保存行程');
    this.addTyping(() => {
      this.addAIMessage('行程已保存到「我的行程」！\n\n出发前记得查看打包清单，旅途愉快~');
      this.setData({ quickChips: ['修改第1天行程', '查看我的行程', '生成打包清单', '分享给朋友', '重新规划'] });
    });
  },

  onEditTrip() {
    const tripId = this.data._currentTripId;
    if (tripId) {
      wx.navigateTo({ url: `/pages/planner/planner?id=${tripId}` });
    } else {
      wx.showToast({ title: '请先保存行程', icon: 'none' });
    }
  },

  onViewMap() {
    const tripId = this.data._currentTripId;
    if (tripId) {
      const routeId = this.data.routeTabActive || 'seasonal';
      wx.navigateTo({ url: `/pages/map/map?tripId=${tripId}&routeId=${encodeURIComponent(routeId)}` });
    } else {
      wx.showToast({ title: '请先生成路线', icon: 'none' });
    }
  },

  // 快速滚动到行程卡片
  scrollToTripCard() {
    const msgs = this.data.messages || [];
    // 找最后一个 trip_card（最新的行程卡片）
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].type === 'trip_card') {
        this.setData({ scrollToId: msgs[i].id });
        wx.showToast({ title: '已回到行程', icon: 'none', duration: 1000 });
        return;
      }
    }
    wx.showToast({ title: '行程卡片不存在', icon: 'none' });
  },

  // ===== 景点展开/收起 =====
  toggleSpotExpand(e) {
    const { msgId, dayIdx, spotIdx } = e.currentTarget.dataset;
    const msgIndex = (this.data.messages || []).findIndex(m => m.id === msgId);
    if (msgIndex < 0) return;
    const msg = this.data.messages[msgIndex];
    const spot = msg && msg.payload && msg.payload.currentRoute &&
      msg.payload.currentRoute.days[dayIdx] &&
      msg.payload.currentRoute.days[dayIdx].spotDetails &&
      msg.payload.currentRoute.days[dayIdx].spotDetails[spotIdx];
    if (!spot) return;
    const current = spot._expanded || false;
    const key = `messages[${msgIndex}].payload.currentRoute.days[${dayIdx}].spotDetails[${spotIdx}]._expanded`;
    this.setData({ [key]: !current });
  },

  // ===== 🆕 路线工作台：景点展开/收起 =====
  toggleRouteSpotExpand(e) {
    const { dayIdx, spotIdx } = e.currentTarget.dataset;
    const route = this.data._currentRoute;
    if (!route || !route.days || !route.days[dayIdx]) return;
    const spot = route.days[dayIdx].spotDetails && route.days[dayIdx].spotDetails[spotIdx];
    if (!spot) return;
    const key = `_currentRoute.days[${dayIdx}].spotDetails[${spotIdx}]._expanded`;
    this.setData({ [key]: !spot._expanded });
  },

  // ===== 分享 =====

  onShareAppMessage() {
    const dest = this.data.tripParams && this.data.tripParams.destination;
    let title = '拾路派 - AI 旅行助手';
    if (dest) title = '在' + dest + '怎么玩？问问拾路派 AI 旅行助手';
    return share.shareToFriend({ title, path: '/pages/chat/chat' });
  },

  onShareTimeline() {
    const dest = this.data.tripParams && this.data.tripParams.destination;
    let title = '拾路派 - AI 旅行助手';
    if (dest) title = '在' + dest + '怎么玩？拾路派 AI 旅行规划';
    return share.shareToTimeline({ title });
  }
});
