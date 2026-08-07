/**
 * API 模块统一导出（命名空间模式 — 防止展开运算符合并时的静默覆盖）
 *
 * 使用方式：
 *   const { trip, user, social } = require('../../utils/api');
 *   await trip.getTripList();
 *   await user.getProfile();
 *
 * 兼容旧用法（模块动态查找，无性能损耗）：
 *   const api = require('../../utils/api');
 *   await api.getTripList();  // 自动委托到 trip.getTripList()
 */

const auth = require('./auth');
const trip = require('../api/trip');
const user = require('../api/user');
const spot = require('../api/spot');
const social = require('../api/social');
const message = require('../api/message');
const city = require('../api/city');
const im = require('../api/im');
const hiking = require('../api/hiking');

const modules = { auth, trip, user, spot, social, message, city, im, hiking };

// 检查命名冲突（仅开发环境警告）
const allKeys = new Map();
for (const [name, mod] of Object.entries(modules)) {
  for (const key of Object.keys(mod)) {
    if (allKeys.has(key)) {
      console.warn(`[api] 命名冲突: "${key}" 在 ${allKeys.get(key)} 和 ${name} 中同时存在`);
    }
    allKeys.set(key, name);
  }
}

// 代理层：兼容旧用法 api.getTripList() 自动委托到 trip.getTripList()
module.exports = new Proxy(modules, {
  get(target, prop) {
    if (prop in target) return target[prop];
    // fallback：在所有模块中查找
    for (const mod of Object.values(target)) {
      if (mod && typeof mod[prop] === 'function') {
        return mod[prop];
      }
    }
  }
});
