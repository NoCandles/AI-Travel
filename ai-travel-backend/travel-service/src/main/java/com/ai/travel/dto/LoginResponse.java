package com.ai.travel.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应 DTO
 */
@Data
@Builder
public class LoginResponse {
    
    /**
     * 用户 ID
     */
    private String userId;
    
    /**
     * 登录 token
     */
    private String token;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像
     */
    private String avatar;

    /**
     * 手机号（微信授权获取的真实手机号）
     */
    private String phone;

    /**
     * 提示信息
     */
    private String message;
}
