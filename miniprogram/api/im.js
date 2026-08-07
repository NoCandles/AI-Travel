const { request } = require('../utils/request');

function getConversations() {
  return request({
    url: '/api/im/conversations',
    method: 'GET'
  });
}

function getMessages(targetUserId, page, size) {
  return request({
    url: '/api/im/messages?targetUserId=' + targetUserId + '&page=' + (page || 1) + '&size=' + (size || 20),
    method: 'GET'
  });
}

function sendText(targetUserId, content) {
  return request({
    url: '/api/im/messages',
    method: 'POST',
    data: { targetUserId, content }
  });
}

function sendRoute(targetUserId, routeId) {
  return request({
    url: '/api/im/messages/route',
    method: 'POST',
    data: { targetUserId, routeId }
  });
}

function markRead(conversationId) {
  return request({
    url: '/api/im/conversations/' + conversationId + '/read',
    method: 'PUT'
  });
}

function getImUnreadCount() {
  return request({
    url: '/api/im/unread-count',
    method: 'GET'
  });
}

module.exports = {
  getConversations,
  getMessages,
  sendText,
  sendRoute,
  markRead,
  getImUnreadCount
};
