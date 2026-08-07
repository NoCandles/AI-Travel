const { request } = require('../utils/request');

/** 获取用户资料 */
function getUserProfile() {
  return request({
    url: '/api/users/profile',
    method: 'GET'
  });
}

/** 更新用户资料 */
function updateUserProfile(data) {
  return request({
    url: '/api/users/profile',
    method: 'PUT',
    data: data
  });
}

/** 获取他人主页资料 */
function getPublicUserProfile(userId) {
  return request({
    url: '/api/users/' + userId + '/profile',
    method: 'GET'
  });
}

/** 更新用户封面图 */
function updateCoverImage(coverImage) {
  return request({
    url: '/api/users/cover',
    method: 'PUT',
    data: { coverImage }
  });
}

/** 获取用户统计数据（行程、发布、收藏、关注） */
function getUserStats() {
  return request({
    url: '/api/users/stats',
    method: 'GET'
  });
}

/** 获取用户偏好设置 */
function getPreferences() {
  return request({
    url: '/api/users/preferences',
    method: 'GET'
  });
}

/** 保存用户偏好设置 */
function savePreferences(preferences) {
  return request({
    url: '/api/users/preferences',
    method: 'PUT',
    data: preferences
  });
}

/** 获取用户足迹（去过的不重复城市列表） */
function getFootprint() {
  return request({
    url: '/api/users/footprint',
    method: 'GET'
  });
}

/** 获取用户足迹详情（城市 + 次数） */
function getFootprintDetail() {
  return request({
    url: '/api/users/footprint/detail',
    method: 'GET'
  });
}

/** 获取用户系统设置（通知开关、定位开关等） */
function getUserSettings() {
  return request({
    url: '/api/users/settings',
    method: 'GET'
  });
}

/** 保存用户系统设置（通知开关、定位开关等） */
function saveUserSettings(settings) {
  return request({
    url: '/api/users/settings',
    method: 'PUT',
    data: settings
  });
}

module.exports = {
  getUserProfile,
  getPublicUserProfile,
  updateUserProfile,
  updateCoverImage,
  getUserStats,
  getPreferences,
  savePreferences,
  getFootprint,
  getFootprintDetail,
  getUserSettings,
  saveUserSettings
};
