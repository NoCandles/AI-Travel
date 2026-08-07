const app = getApp();
const C = require('../config/constants');

// 本地调试开关：优先读取 app.globalData.debugMode，否则默认云托管
const USE_LOCAL = (app.globalData && app.globalData.debugMode) || false;
const LOCAL_BASE = C.LOCAL_BASE_URL;
const AI_TIMEOUT = 60000;        // AI 调用 60s
const MAX_RETRIES = 1;           // 自动重试次数

/**
 * 核心请求函数
 * @param {Object} options - { url, data, method, timeout, silent, retryCount (internal) }
 * @param {boolean} options.silent - 为 true 时关闭 request.js 内的所有 toast，由调用方自行处理错误提示
 */
function request(options) {
  const { url, data, method = 'GET', timeout = C.REQUEST_TIMEOUT, silent = false } = options;
  const retryCount = options.retryCount || 0;

  // ===== 统一登录检查 =====
  // 仅登录接口和城市公共查询不需要登录态，其余接口均需登录
  const isPublicSocialRead = method === 'GET' && (
    url.startsWith('/api/social/publish?') ||
    url.startsWith('/api/social/publish/detail') ||
    url.startsWith('/api/social/publish/hot-tags') ||
    url.startsWith('/api/social/comment/list') ||
    url.startsWith('/api/v1/social/publish?') ||
    url.startsWith('/api/v1/social/publish/detail') ||
    url.startsWith('/api/v1/social/publish/hot-tags') ||
    url.startsWith('/api/v1/social/comment/list')
  );
  const isAuthApi = url.startsWith('/api/auth') || url.startsWith('/api/cities') || isPublicSocialRead;
  if (!isAuthApi) {
    const userId = wx.getStorageSync('userId') || '';
    const token = wx.getStorageSync('token') || '';
    if (!userId || !token) {
      return Promise.reject(new Error('NOT_LOGIN:请先登录'));
    }
  }

  const userId = wx.getStorageSync('userId') || 'guest_' + Date.now();
  const token = wx.getStorageSync('token') || '';

  // 统一添加 /v1 前缀：/api/xxx → /api/v1/xxx
  let finalUrl = url;
  if (finalUrl.startsWith('/api/') && !finalUrl.startsWith('/api/v1/')) {
    finalUrl = '/api/v1/' + finalUrl.slice(5);
  }

  return new Promise((resolve, reject) => {
    const headers = {
      'Content-Type': 'application/json',
      'X-User-Id': userId
    };
    // 注入 Token
    if (token) {
      headers['X-Token'] = token;
    }

    const doRequest = USE_LOCAL ? doLocalRequest : doCloudRequest;
    doRequest(finalUrl, method, data, headers, timeout, retryCount, silent, resolve, reject);
  });
}

/** 本地调试模式请求 */
function doLocalRequest(url, method, data, headers, timeout, retryCount, silent, resolve, reject) {
  wx.request({
    url: LOCAL_BASE + url,
    method: method,
    header: headers,
    data: data || {},
    timeout: timeout,
    success: (res) => handleSuccess(res, url, method, data, headers, timeout, retryCount, silent, resolve, reject),
    fail: (err) => handleFail(err, '请求失败，检查后端是否启动', url, method, data, headers, timeout, retryCount, silent, resolve, reject)
  });
}

/** 云托管模式请求 */
function doCloudRequest(url, method, data, headers, timeout, retryCount, silent, resolve, reject) {
  headers['X-WX-SERVICE'] = C.CLOUD_SERVICE_NAME;
  wx.cloud.callContainer({
    config: { env: app.globalData.cloudEnvId },
    path: url,
    method: method,
    header: headers,
    data: data || {},
    timeout: timeout,
    success: (res) => handleSuccess(res, url, method, data, headers, timeout, retryCount, silent, resolve, reject),
    fail: (err) => handleFail(err, '请求失败', url, method, data, headers, timeout, retryCount, silent, resolve, reject)
  });
}

/** 成功回调处理 */
function handleSuccess(res, url, method, data, headers, timeout, retryCount, silent, resolve, reject) {
  if (res.statusCode === 200) {
    if (res.data && res.data.success !== false) {
      resolve(res.data.data);
    } else {
      const msg = (res.data && res.data.message) || '请求失败';
      // M4: silent 模式下不弹 toast，由调用方决定如何展示错误
      if (!silent) wx.showToast({ title: msg, icon: 'none' });
      reject(new Error(msg));
    }
  } else if (res.statusCode >= 500 && retryCount < MAX_RETRIES) {
    // 5xx 服务端错误，自动重试一次
    console.warn(`[请求重试] ${url} status=${res.statusCode} 第${retryCount + 1}次重试`);
    request({ url, data, method, timeout, silent, retryCount: retryCount + 1 })
      .then(resolve)
      .catch(reject);
  } else {
    const msg = res.statusCode === 401 ? '登录已过期，请重新登录' :
                  res.statusCode === 403 ? '没有权限' :
                  '网络错误 ' + res.statusCode;
    if (res.statusCode === 401) {
      // Token 过期，引导重新登录（统一处理）
      const notLoginErr = new Error('NOT_LOGIN:请先登录');
      notLoginErr.statusCode = 401;
      reject(notLoginErr);
    } else {
      if (!silent) wx.showToast({ title: msg, icon: 'none' });
      reject(new Error(msg));
    }
  }
}

/** 失败回调处理 */
function handleFail(err, defaultMsg, url, method, data, headers, timeout, retryCount, silent, resolve, reject) {
  console.error('请求失败:', url, err);

  // 网络错误自动重试一次
  if (retryCount < MAX_RETRIES) {
    console.warn(`[网络重试] ${url} 第${retryCount + 1}次重试`);
    request({ url, data, method, timeout, silent, retryCount: retryCount + 1 })
      .then(resolve)
      .catch(reject);
    return;
  }

  // L10: 检查网络状态，给出更明确的提示
  let msg = defaultMsg;
  try {
    const app = getApp();
    if (app && app.globalData.isOnline === false) {
      msg = '网络已断开，请检查网络设置';
    }
  } catch (e) {
    // getApp() 可能在 App() 实例化前被调用，跳过网络检查
  }

  // M4: silent 模式下不弹 toast
  if (!silent) wx.showToast({ title: msg, icon: 'none' });
  reject(err);
}

/**
 * 统一处理“未登录”错误
 * 所有 API 调用方在 catch 中可用此函数，自动弹 Modal 跳转登录
 * @param {Error} err - reject 的错误对象
 * @param {Object} [options]
 * @param {string} [options.redirectUrl] - 登录成功后跳回的页面路径
 */
function handleNotLogin(err, options = {}) {
  if (!err || !err.message || !err.message.includes('NOT_LOGIN')) {
    // 不是登录错误，直接 reject
    return Promise.reject(err);
  }

  return new Promise((resolve) => {
    wx.showModal({
      title: '提示',
      content: '请先登录',
      confirmText: '去登录',
      showCancel: true,
      success: (res) => {
        if (res.confirm) {
          const app = getApp();
          if (app && app.logout) app.logout();
          const redirectUrl = options.redirectUrl || '';
          let url = '/pages/login/login';
          if (redirectUrl) url += '?redirect=' + encodeURIComponent(redirectUrl);
          wx.navigateTo({ url });
        }
        // 用户取消，仍 reject
        resolve();
      }
    });
  });
}

/** AI 调用专用请求（更长超时 + 更少 toast） */
function aiRequest(options) {
  return request({ ...options, timeout: AI_TIMEOUT });
}

/** 文件上传 */
function uploadFile(options) {
  return new Promise((resolve, reject) => {
    const { url, filePath, name = 'file' } = options;
    const userId = wx.getStorageSync('userId') || '';
    const token = wx.getStorageSync('token') || '';

    const header = {
      'X-WX-SERVICE': C.CLOUD_SERVICE_NAME,
      'X-User-Id': userId
    };
    if (token) header['X-Token'] = token;

    wx.cloud.uploadFile({
      cloudPath: `uploads/${Date.now()}_${Math.random().toString(36).substr(2, 9)}.${filePath.split('.').pop()}`,
      filePath,
      success: (res) => {
        resolve(res.fileID);
      },
      fail: (err) => reject(err)
    });
  });
}

module.exports = {
  request,
  aiRequest,
  uploadFile,
  handleNotLogin   // 统一未登录处理
};
