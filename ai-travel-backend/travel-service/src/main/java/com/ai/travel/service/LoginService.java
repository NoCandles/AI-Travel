package com.ai.travel.service;

import com.ai.travel.dto.LoginRequest;
import com.ai.travel.dto.LoginResponse;

/**
 * 登录服务接口
 */
public interface LoginService {

    /**
     * 手机号 + 密码登录
     * 如果手机号不存在，则自动注册新用户
     * @param request 登录请求（phone=手机号, password=密码）
     * @return 登录响应
     */
    LoginResponse loginByPhone(LoginRequest request);

    /**
     * 游客登录
     * @return 登录响应
     */
    LoginResponse guestLogin();

    /**
     * 微信身份验证后重置密码
     * @param phone 手机号
     * @param wechatCode 微信 code
     * @param newPassword 新密码（SHA256）
     * @return 结果消息
     */
    String resetPassword(String phone, String wechatCode, String newPassword);
}
