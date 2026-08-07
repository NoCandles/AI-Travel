// 清除缓存脚本
// 在微信开发者工具控制台执行此脚本


// 清除所有 storage
wx.clearStorageSync();

// 重新加载页面
wx.reLaunch({
  url: '/pages/chat/chat'
});

