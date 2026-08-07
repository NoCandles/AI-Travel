/**
 * 积分系统 API 模块
 */

const { request } = require('../utils/request');

/**
 * 获取我的积分信息
 */
function getMyPoints() {
  return request({
    url: '/api/points/my',
    method: 'GET'
  });
}

/**
 * 每日签到
 */
function signIn() {
  return request({
    url: '/api/points/sign-in',
    method: 'POST'
  });
}

/**
 * 获取积分变动记录
 */
function getPointRecords(page, size) {
  return request({
    url: '/api/points/records?page=' + (page || 1) + '&size=' + (size || 20),
    method: 'GET'
  });
}

/**
 * 获取等级配置
 */
function getLevelConfig() {
  return request({
    url: '/api/points/level-config',
    method: 'GET'
  });
}

/**
 * 解锁模板
 */
function unlockTemplate(templateId) {
  return request({
    url: '/api/points/unlock-template',
    method: 'POST',
    data: {
      templateId: templateId
    }
  });
}

module.exports = {
  getMyPoints,
  signIn,
  getPointRecords,
  getLevelConfig,
  unlockTemplate
};
