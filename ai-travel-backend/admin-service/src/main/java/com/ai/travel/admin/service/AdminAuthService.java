package com.ai.travel.admin.service;

import com.ai.travel.admin.config.AdminJwtUtil;
import com.ai.travel.admin.dto.LoginRequest;
import com.ai.travel.admin.dto.TokenResponse;
import com.ai.travel.admin.entity.AdminUser;
import com.ai.travel.admin.repository.AdminUserRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final AdminJwtUtil adminJwtUtil;
    private final PasswordEncoder passwordEncoder;

    /**
     * 管理员登录
     */
    public TokenResponse login(LoginRequest request) {
        // 查询用户
        AdminUser admin = adminUserRepository.selectOne(
                new LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, request.getUsername())
        );
        if (admin == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 更新最后登录时间
        admin.setLastLogin(LocalDateTime.now());
        adminUserRepository.updateById(admin);

        // 生成 Token
        String token = adminJwtUtil.generateToken(admin.getId(), admin.getUsername());

        return new TokenResponse(
                token,
                admin.getId(),
                admin.getUsername(),
                admin.getNickname(),
                admin.getRole(),
                86400000L // 24h
        );
    }

    /**
     * 根据ID查询管理员
     */
    public AdminUser getById(String id) {
        return adminUserRepository.selectById(id);
    }
}
