const { request } = require('../utils/request');

// ==================== 消息中心 ====================

/** 获取消息列表 */
function getMessages(type, page, size) {
  const params = [
    'type=' + (type || 'all'),
    'page=' + (page || 1),
    'size=' + (size || 20)
  ];
  return request({
    url: '/api/messages?' + params.join('&'),
    method: 'GET'
  });
}

/** 获取未读消息数量 */
function getUnreadCount() {
  return request({
    url: '/api/messages/unread-count',
    method: 'GET'
  });
}

/** 标记消息为已读 */
function markMessageAsRead(id) {
  return request({
    url: '/api/messages/' + id + '/read',
    method: 'PUT'
  });
}

/** 标记所有消息为已读 */
function markAllAsRead(type) {
  let url = '/api/messages/read-all';
  if (type) url += '?type=' + type;
  return request({
    url: url,
    method: 'PUT'
  });
}

/** 删除消息 */
function deleteMessage(id) {
  return request({
    url: '/api/messages/' + id,
    method: 'DELETE'
  });
}

/** 清空消息 */
function clearMessages(type) {
  let url = '/api/messages/clear';
  if (type) url += '?type=' + type;
  return request({
    url: url,
    method: 'DELETE'
  });
}

module.exports = {
  getMessages,
  getUnreadCount,
  markMessageAsRead,
  markAllAsRead,
  deleteMessage,
  clearMessages
};
