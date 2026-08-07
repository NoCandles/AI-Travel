/**
 * 拾路派 - AI 对话 API 模块
 * 
 * 接口：
 *   POST /api/chat/send    - 发送消息，AI解析意图
 *   POST /api/chat/confirm  - 确认参数，触发路线生成
 */

const { request } = require('../utils/request');

// AI 对话接口涉及 DeepSeek 调用，超时可能较长，使用 60s（匹配后端 RestTemplate 响应超时）
const AI_TIMEOUT = 60000;

const chat = {

  /**
   * 发送对话消息
   * @param {string} text - 用户输入的自然语言
   * @param {string} conversationId - 对话会话ID（可选，首次为空）
   * @param {string} [tripId] - 当前关联行程ID（done 阶段用于行程修改）
   * @returns {Promise<{
   *   conversationId: string,
   *   messages: Array<{type:string, content:string, payload:object}>,
   *   paramsExtracted: object
   * }>}
   */
  send(text, conversationId, tripId) {
    return request({
      url: '/api/chat/send',
      method: 'POST',
      timeout: AI_TIMEOUT,
      data: {
        text: text,
        conversationId: conversationId || null,
        tripId: tripId || null
      }
    });
  },

  /**
   * 确认参数并触发生成
   * @param {string} conversationId - 对话会话ID
   * @param {object} params - 确认的行程参数
   * @returns {Promise<{tripId: string}>}
   */
  confirm(conversationId, params) {
    return request({
      url: '/api/chat/confirm',
      method: 'POST',
      timeout: AI_TIMEOUT,
      data: {
        conversationId: conversationId,
        destination: params.destination,
        startPoint: params.startPoint || '',
        endPoint: params.endPoint || '',
        days: params.days,
        startDate: params.startDate,
        endDate: params.endDate,
        preference: params.preference,
        budget: params.budget,
        travelMode: params.travelMode,
        mustVisit: params.mustVisit || [],
        hikingProfile: params.hikingProfile || null
      }
    });
  },

  /**
   * 获取对话历史
   * @param {string} conversationId
   * @returns {Promise<{messages: Array}>}
   */
  getHistory(conversationId) {
    return request({
      url: `/api/chat/history/${conversationId}`,
      method: 'GET'
    });
  },

  /**
   * 获取当前用户的聊天会话列表
   */
  listConversations() {
    return request({
      url: '/api/chat/conversations',
      method: 'GET'
    });
  },

  /**
   * 保存当前聊天页快照，确保卡片消息也能恢复
   */
  saveSnapshot(conversationId, snapshot) {
    return request({
      url: `/api/chat/conversations/${conversationId}/snapshot`,
      method: 'PUT',
      data: snapshot || {},
      silent: true
    });
  },

  /**
   * 删除一条聊天记录（后端软删除，其他会话不受影响）
   */
  deleteConversation(conversationId) {
    return request({
      url: `/api/chat/conversations/${conversationId}`,
      method: 'DELETE'
    });
  },

  /**
   * 获取 WebSocket 路径
   * @param {string} tripId - 行程ID
   * @returns {string} wsPath - e.g. "/ws/chat/abc123"
   */
  getWsPath(tripId) {
    return `/ws/chat/${tripId}`;
  }
};

module.exports = chat;
