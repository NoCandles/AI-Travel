/**
 * 拾路派 - WebSocket 连接管理器 v4.0
 * 🚀 支持多通道：可同时维护行程进度 WS 和聊天 AI WS
 * 适配 CloudBase 云托管
 */

const app = getApp();

// 连接状态常量
const STATE = {
  IDLE: 'idle',
  CONNECTING: 'connecting',
  CONNECTED: 'connected',
  CLOSED: 'closed'
};

class WebSocketManager {
  constructor() {
    /** 多通道连接池 keyed by channelId */
    this._channels = new Map();
  }

  /**
   * 建立 WebSocket 连接（多通道支持）
   * @param {string} channelId - 通道唯一标识（如 tripId 或 conversationId）
   * @param {object} callbacks - 回调函数 { onConnected, onProgress, onDone, onParamOptimized, onError, onClose }
   * @param {string} [customPath] - 自定义路径（如 '/ws/chat/msg/{convId}'）
   */
  connect(channelId, callbacks = {}, customPath = null) {
    // 如果该通道已有连接，先断开
    this.disconnect(channelId);

    const channel = {
      id: channelId,
      state: STATE.CONNECTING,
      socketTask: null,
      callbacks: callbacks,
      customPath: customPath,
      reconnectTimer: null,
      reconnectAttempts: 0,
      maxReconnects: app.globalData.debugMode ? 0 : 5,
      heartbeatTimer: null,
      isManualDisconnect: false
    };

    this._channels.set(channelId, channel);
    this._doConnect(channelId, customPath);
  }

  _doConnect(channelId, customPath = null) {
    const channel = this._channels.get(channelId);
    if (!channel) return;

    const debugMode = app.globalData.debugMode || false;
    const wsPath = customPath || `/ws/chat/${channelId}`;

    try {
      const baseUrl = app.globalData.localWsBaseUrl;

      if (debugMode && !baseUrl) {
        console.error('[WS] 基础 URL 未配置:', { debugMode, baseUrl });
        channel.state = STATE.CLOSED;
        this._attemptReconnect(channelId);
        return;
      }

      const token = app.globalData.token;
      const userId = app.globalData.userInfo?.id;
      let query = '';

      if (token) {
        query = 'token=' + encodeURIComponent(token);
      } else if (userId) {
        query = 'userId=' + encodeURIComponent(userId);
      }

      const requestPath = wsPath + (query ? (wsPath.includes('?') ? '&' : '?') + query : '');

      // 云托管 WebSocket 必须通过 connectContainer 连接，避免依赖可能过期的公网域名。
      if (!debugMode && wx.cloud && typeof wx.cloud.connectContainer === 'function') {
        wx.cloud.connectContainer({
          config: { env: app.globalData.cloudEnvId },
          service: app.globalData.cloudServiceName,
          path: requestPath
        }).then((result) => {
          const socketTask = result && result.socketTask;
          if (!socketTask) {
            channel.state = STATE.CLOSED;
            if (channel.callbacks.onError) channel.callbacks.onError('云托管 WebSocket 未创建');
            this._attemptReconnect(channelId);
            return;
          }
          channel.socketTask = socketTask;
          this._bindSocketEvents(channelId, socketTask);
        }).catch((err) => {
          console.error(`[WS] 云托管连接失败 channel=${channelId}:`, err);
          channel.state = STATE.CLOSED;
          if (channel.callbacks.onError) channel.callbacks.onError('WebSocket 连接错误');
          this._attemptReconnect(channelId);
        });
        return;
      }

      const wsUrl = baseUrl + requestPath;

      const socketTask = wx.connectSocket({
        url: wsUrl,
        header: { 'Content-Type': 'application/json' },
        success: () => {},
        fail: (err) => {
          console.error(`[WS] connectSocket 失败 channel=${channelId}:`, err);
          channel.state = STATE.CLOSED;
          this._attemptReconnect(channelId);
        }
      });

      if (!socketTask) {
        channel.state = STATE.CLOSED;
        this._attemptReconnect(channelId);
        return;
      }

      channel.socketTask = socketTask;
      this._bindSocketEvents(channelId, socketTask);

    } catch (e) {
      console.error(`[WS] 创建连接失败 channel=${channelId}:`, e);
      channel.state = STATE.CLOSED;
      this._attemptReconnect(channelId);
    }
  }

  _bindSocketEvents(channelId, socketTask) {
    const channel = this._channels.get(channelId);
    if (!channel) return;

    if (typeof socketTask.onOpen === 'function') {
      socketTask.onOpen(() => {
        channel.state = STATE.CONNECTED;
        channel.reconnectAttempts = 0;
        this._startHeartbeat(channelId);
        if (channel.callbacks.onConnected) {
          channel.callbacks.onConnected({ tripId: channelId });
        }
      });
    }

    if (typeof socketTask.onMessage === 'function') {
      socketTask.onMessage((res) => {
        this._handleMessage(channelId, res.data);
      });
    }

    if (typeof socketTask.onClose === 'function') {
      socketTask.onClose((res) => {
        channel.state = STATE.CLOSED;
        this._stopHeartbeat(channelId);
        // 主动断开（done/error 后的 closeClean）不触发 onClose 回调，避免误报离线
        if (!channel.isManualDisconnect && channel.callbacks.onClose) {
          channel.callbacks.onClose(res.code, res.reason);
        }
        if (!channel.isManualDisconnect && res.code !== 1000 && res.code !== 1005) {
          this._attemptReconnect(channelId);
        }
      });
    }

    if (typeof socketTask.onError === 'function') {
      socketTask.onError((err) => {
        console.error(`[WS] 错误 channel=${channelId}:`, err);
        channel.state = STATE.CLOSED;
        this._stopHeartbeat(channelId);
        if (channel.callbacks.onError) {
          channel.callbacks.onError('WebSocket 连接错误');
        }
        if (!channel.isManualDisconnect) {
          this._attemptReconnect(channelId);
        }
      });
    }
  }

  _handleMessage(channelId, rawData) {
    const channel = this._channels.get(channelId);
    if (!channel) return;

    try {
      const msg = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;
      const { type, data } = msg;

      switch (type) {
        case 'connected':
          break;
        case 'progress':
          if (channel.callbacks.onProgress) {
            channel.callbacks.onProgress(data);
          }
          break;
        case 'token':
          if (channel.callbacks.onToken) {
            channel.callbacks.onToken(data);
          }
          break;
        case 'done':
          this._stopHeartbeat(channelId);
          if (channel.callbacks.onDone) {
            channel.callbacks.onDone(data);
          }
          this._closeClean(channelId);
          break;
        case 'error':
          this._stopHeartbeat(channelId);
          if (channel.callbacks.onError) {
            channel.callbacks.onError(data && data.message ? data.message : '未知错误');
          }
          this._closeClean(channelId);
          break;
        case 'pong':
          break;
        case 'param_optimized':
          if (channel.callbacks.onParamOptimized) {
            channel.callbacks.onParamOptimized(data);
          }
          break;
        default:
          break;
      }
    } catch (e) {
      console.warn(`[WS] 消息解析失败 channel=${channelId}:`, e, rawData);
    }
  }

  _startHeartbeat(channelId) {
    this._stopHeartbeat(channelId);
    const channel = this._channels.get(channelId);
    if (!channel) return;
    channel.heartbeatTimer = setInterval(() => {
      if (channel.state === STATE.CONNECTED && channel.socketTask) {
        try {
          if (typeof channel.socketTask.send === 'function') {
            channel.socketTask.send({ data: JSON.stringify({ type: 'ping' }) });
          }
        } catch (e) {
          console.warn(`[WS] 心跳发送失败 channel=${channelId}:`, e);
        }
      }
    }, 30000);
  }

  _stopHeartbeat(channelId) {
    const channel = this._channels.get(channelId);
    if (channel && channel.heartbeatTimer) {
      clearInterval(channel.heartbeatTimer);
      channel.heartbeatTimer = null;
    }
  }

  _attemptReconnect(channelId) {
    const channel = this._channels.get(channelId);
    if (!channel) return;
    if (channel.isManualDisconnect) return;
    if (channel.reconnectAttempts >= channel.maxReconnects) {
      console.warn(`[WS] 重连次数已达上限 channel=${channelId}`);
      if (channel.callbacks.onError) {
        channel.callbacks.onError('连接失败，请检查网络后重试');
      }
      return;
    }
    channel.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, channel.reconnectAttempts), 15000);

    if (channel.reconnectTimer) clearTimeout(channel.reconnectTimer);
    channel.reconnectTimer = setTimeout(() => {
      if (!channel.isManualDisconnect) {
      this._doConnect(channelId, channel.customPath);
      }
    }, delay);
  }

  _closeClean(channelId) {
    const channel = this._channels.get(channelId);
    if (!channel) return;
    channel.isManualDisconnect = true;
    channel.state = STATE.CLOSED;
    this._stopHeartbeat(channelId);
    if (channel.reconnectTimer) {
      clearTimeout(channel.reconnectTimer);
      channel.reconnectTimer = null;
    }
    if (channel.socketTask) {
      try {
        if (typeof channel.socketTask.close === 'function') {
          channel.socketTask.close({ code: 1000, reason: 'normal' });
        }
      } catch (e) { /* ignore */ }
      channel.socketTask = null;
    }
  }

  /**
   * 断开指定通道的连接。不传 channelId 时断开全部。
   * @param {string} [channelId]
   */
  disconnect(channelId) {
    if (channelId) {
      this._closeClean(channelId);
      this._channels.delete(channelId);
    } else {
      // 断开所有
      for (const id of this._channels.keys()) {
        this._closeClean(id);
      }
      this._channels.clear();
    }
  }

  /**
   * 获取指定通道的连接状态
   */
  getState(channelId) {
    const channel = this._channels.get(channelId);
    return channel ? channel.state : STATE.IDLE;
  }

  /**
   * 指定通道是否已连接
   */
  isConnected(channelId) {
    const channel = this._channels.get(channelId);
    return channel ? channel.state === STATE.CONNECTED : false;
  }

  /**
   * 通过指定通道发送消息
   * @param {string} channelId
   * @param {object|string} data
   */
  send(channelId, data) {
    const channel = this._channels.get(channelId);
    if (!channel || channel.state !== STATE.CONNECTED) {
      console.warn(`[WS] 通道未连接 channel=${channelId}`);
      return false;
    }
    if (!channel.socketTask || typeof channel.socketTask.send !== 'function') {
      return false;
    }
    try {
      const message = typeof data === 'string' ? data : JSON.stringify(data);
      channel.socketTask.send({ data: message });
      return true;
    } catch (e) {
      console.error(`[WS] 发送消息失败 channel=${channelId}:`, e);
      return false;
    }
  }
}

// 单例
const wsManager = new WebSocketManager();

module.exports = wsManager;
