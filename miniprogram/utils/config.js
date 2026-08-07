/**
 * 全局配置文件
 * 集中管理跨模块共享的常量，避免硬编码散落各处
 * 实际值统一由 config/constants.js 管理
 */
const C = require('../config/constants');

module.exports = {
  NAV_BRIDGE_HOST: C.NAV_BRIDGE_HOST
};
