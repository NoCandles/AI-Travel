package com.ai.travel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO
 * <p>
 * 手机号 + 密码登录
 */
@Data
public class LoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Size(min = 11, max = 11, message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 昵称（注册时可选） */
    private String nickname;

    /** 头像（注册时可选） */
    private String avatar;

    /** 微信 code（wx.login 返回，用于绑定/验证 openId） */
    private String wechatCode;
}
