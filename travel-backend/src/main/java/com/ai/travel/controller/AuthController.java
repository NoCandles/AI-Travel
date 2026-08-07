package com.ai.travel.controller;

import com.ai.travel.dto.ApiResponse;
import com.ai.travel.dto.LoginRequest;
import com.ai.travel.dto.LoginResponse;
import com.ai.travel.dto.ResetPasswordRequest;
import com.ai.travel.service.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * <p>
 * 支持两种登录方式：
 * 1. 手机号 + 密码登录（/login）：自动注册新用户
 * 2. 游客模式（/guest）
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;

    /**
     * 手机号 + 密码登录
     * <p>
     * 如果手机号不存在，则自动注册新用户。
     * 如果手机号已存在，则验证密码后登录。
     *
     * @param request phone=手机号, password=密码
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> loginByPhone(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = loginService.loginByPhone(request);
        if (response.getUserId() == null) {
            return ApiResponse.error(response.getMessage());
        }
        return ApiResponse.success(response, response.getMessage());
    }

    /**
     * 游客登录
     */
    @PostMapping("/guest")
    public ApiResponse<LoginResponse> guestLogin() {
        LoginResponse response = loginService.guestLogin();
        return ApiResponse.success(response, "游客模式");
    }

    /**
     * 忘记密码 - 微信身份验证后重置
     */
    @PostMapping("/reset-password")
    public ApiResponse<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        String result = loginService.resetPassword(
                request.getPhone(),
                request.getWechatCode(),
                request.getNewPassword());
        if (result.equals("密码重置成功")) {
            return ApiResponse.success(null, result);
        }
        return ApiResponse.error(result);
    }
}
