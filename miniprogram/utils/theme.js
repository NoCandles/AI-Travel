/**
 * 暗色模式主题工具
 * 统一管理 darkMode 状态，减少各页面重复代码
 *
 * 🌟 双轨制说明：
 *   1. 系统级自动适配：variables.wxss 的 @media (prefers-color-scheme: dark)
 *      会让所有 CSS 变量自动响应系统主题，无需任何页面级代码
 *   2. App 内手动开关：通过 .theme-dark 类强制覆盖（用于用户手动开启深色）
 *   3. 本工具仅用于 #2 场景：在页面 data 中维护 darkMode 状态，
 *      驱动 WXML 根节点的 theme-dark 类绑定
 */

const app = getApp();

/**
 * 在页面 data 中初始化 darkMode 字段
 * 用法：在 Page data 中调用: ...themeMap,
 * 注意：优先读取 globalData.darkMode（已考虑跟随系统），
 *       storage 仅作为 fallback
 */
const themeMap = {
  darkMode: (app && app.globalData && app.globalData.darkMode) || false
};

/**
 * 在页面 onLoad / onShow 中调用，同步当前主题状态
 * @param {Object} pageCtx - Page 的 this（或 setData 所属对象）
 */
function applyTheme(pageCtx) {
  if (pageCtx && pageCtx.setData && app && app.globalData) {
    pageCtx.setData({ darkMode: app.globalData.darkMode });
  }
}

module.exports = {
  themeMap,
  applyTheme
};

/**
 * 使用示例（各页面替换方式）：
 *
 * 方式一：在 data 中展开 + 在 onLoad/onShow 中调用
 *   const { themeMap, applyTheme } = require('../../utils/theme');
 *   Page({
 *     data: { ...themeMap, ...其他数据 },
 *     onLoad() { applyTheme(this); ... },
 *     onShow() { applyTheme(this); ... }
 *   });
 *
 * 方式二：直接调用
 *   const { applyTheme } = require('../../utils/theme');
 *   onLoad() { applyTheme(this); ... }
 *
 * 🌟 全局自动适配说明：
 *   即使页面没有引入本工具，variables.wxss 的 @media (prefers-color-scheme: dark)
 *   也会让该页面自动跟随系统深色模式（CSS 变量自动切换）。
 *   本工具仅用于"手动开关覆盖系统主题"的场景。
 */
