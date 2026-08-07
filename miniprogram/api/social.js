const { request } = require('../utils/request');

// ==================== 广场发布 ====================

/** 获取广场行程列表 */
function getPublishedTrips(category, keyword, page, size, city, days) {
  const params = [
    'category=' + (category || 'recommend'),
    'keyword=' + encodeURIComponent(keyword || ''),
    'page=' + (page || 1),
    'size=' + (size || 10)
  ];
  if (city) params.push('city=' + encodeURIComponent(city));
  if (days != null && days > 0) params.push('days=' + days);
  return request({
    url: '/api/social/publish?' + params.join('&'),
    method: 'GET'
  });
}

/** 获取行程发布详情 */
function getPublishDetail(publishId, userId) {
  let url = '/api/social/publish/detail?publishId=' + publishId;
  if (userId) url += '&userId=' + userId;
  return request({
    url: url,
    method: 'GET'
  });
}

/** 获取行程发布草稿 */
function getPublishDraft(tripPlanId) {
  return request({
    url: '/api/social/publish/draft?tripPlanId=' + tripPlanId,
    method: 'GET'
  });
}

/** 发布行程到广场 */
function createPublish(data) {
  return request({
    url: '/api/social/publish',
    method: 'POST',
    data: data
  });
}

/** 删除发布 */
function deletePublish(publishId) {
  return request({
    url: '/api/social/publish/' + publishId,
    method: 'DELETE'
  });
}

/** 获取用户个人发布列表 */
function getUserPublish(userId, currentUserId) {
  let url = '/api/social/publish/user/' + userId;
  if (currentUserId) url += '?currentUserId=' + currentUserId;
  return request({
    url: url,
    method: 'GET'
  });
}


/** 获取热门标签 */
function getHotTags() {
  return request({
    url: '/api/social/publish/hot-tags',
    method: 'GET'
  });
}
// ==================== 评论 ====================

/** 获取评论列表（分页） */
function getComments(publishId, userId, page, size) {
  let url = '/api/social/comment/list?publishId=' + publishId +
    '&page=' + (page || 1) + '&size=' + (size || 10);
  if (userId) url += '&userId=' + userId;
  return request({
    url: url,
    method: 'GET'
  });
}

/** 发表评论 */
function addComment(data) {
  return request({
    url: '/api/social/comment',
    method: 'POST',
    data: data
  });
}

/** 删除评论 */
function deleteComment(commentId) {
  return request({
    url: '/api/social/comment/' + commentId,
    method: 'DELETE'
  });
}

/** 回复评论 */
function replyComment(data) {
  return request({
    url: '/api/social/comment/reply',
    method: 'POST',
    data: data
  });
}

// ==================== 关注 ====================

/** 关注用户 */
function followUser(followingId) {
  return request({
    url: '/api/social/follow?followingId=' + followingId,
    method: 'POST'
  });
}

/** 取消关注 */
function unfollowUser(followingId) {
  return request({
    url: '/api/social/follow?followingId=' + followingId,
    method: 'DELETE'
  });
}

/** 检查是否已关注 */
function checkFollow(followingId) {
  return request({
    url: '/api/social/follow/check?followingId=' + followingId,
    method: 'GET'
  });
}

/** 获取关注列表 */
function getFollowingList(userId, type) {
  return request({
    url: '/api/social/follow/list?userId=' + userId + '&type=' + (type || 'following'),
    method: 'GET'
  });
}

// ==================== 点赞 ====================

/** 点赞/取消点赞（兼容对象参数和独立参数两种调用方式） */
function toggleLike(targetIdOrObj, targetType) {
  let targetId, type;
  if (typeof targetIdOrObj === 'object') {
    // 兼容 { targetId, targetType } 对象参数
    targetId = targetIdOrObj.targetId;
    type = targetIdOrObj.targetType || 'publish';
  } else {
    // 兼容 toggleLike(targetId, targetType) 独立参数
    targetId = targetIdOrObj;
    type = targetType || 'publish';
  }
  return request({
    url: '/api/social/like?targetId=' + targetId + '&targetType=' + type,
    method: 'POST'
  });
}

/** 收藏行程 */
function addFav(publishId) {
  return request({
    url: '/api/social/like?targetId=' + publishId + '&targetType=publish&actionType=fav',
    method: 'POST'
  });
}

/** 取消收藏 */
function removeFav(publishId) {
  return request({
    url: '/api/social/like?targetId=' + publishId + '&targetType=publish&actionType=fav',
    method: 'DELETE'
  });
}

/** 获取用户收藏的行程列表（分页） */
function getFavorites(page, size) {
  return request({
    url: '/api/social/publish/favorites?page=' + (page || 1) + '&size=' + (size || 10),
    method: 'GET'
  });
}

/** 获取用户点赞过的行程列表（分页） */
function getLikedRoutes(page, size) {
  return request({
    url: '/api/social/publish/liked?page=' + (page || 1) + '&size=' + (size || 10),
    method: 'GET'
  });
}

module.exports = {
  getPublishedTrips,
  getPublishDetail,
  getPublishDraft,
  createPublish,
  deletePublish,
  getUserPublish,
  getHotTags,
  getComments,
  addComment,
  deleteComment,
  replyComment,
  followUser,
  unfollowUser,
  checkFollow,
  getFollowingList,
  toggleLike,
  addFav,
  removeFav,
  getFavorites,
  getLikedRoutes
};
