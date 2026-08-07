package com.ai.travel.admin.controller;

import com.ai.travel.admin.dto.ApiResponse;
import com.ai.travel.admin.dto.LoginRequest;
import com.ai.travel.admin.dto.TokenResponse;
import com.ai.travel.admin.service.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminAuthService adminAuthService;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse token = adminAuthService.login(request);
        return ApiResponse.success(token, "登录成功");
    }
}
