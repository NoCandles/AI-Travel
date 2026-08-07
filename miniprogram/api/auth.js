const { request } = require('../utils/request');

/** 手机号 + 密码登录（自动注册新用户） */
function loginByPhone(phone, password, wechatCode) {
  const data = { phone, password };
  if (wechatCode) {
    data.wechatCode = wechatCode;
  }
  return request({
    url: '/api/auth/login',
    method: 'POST',
    data: data
  });
}

/** 游客登录 */
function guestLogin() {
  return request({
    url: '/api/auth/guest',
    method: 'POST'
  });
}

/** 忘记密码 - 微信验证后重置 */
function resetPassword(phone, wechatCode, newPassword) {
  return request({
    url: '/api/auth/reset-password',
    method: 'POST',
    data: { phone, wechatCode, newPassword }
  });
}

/** 微信手机号一键登录 */
function wechatPhoneLogin(wechatCode, phoneData) {
  return request({
    url: '/api/auth/wechat-phone',
    method: 'POST',
    data: {
      wechatCode: wechatCode,
      encryptedData: phoneData.encryptedData,
      iv: phoneData.iv
    }
  });
}

module.exports = {
  loginByPhone,
  guestLogin,
  resetPassword,
  wechatPhoneLogin
};
