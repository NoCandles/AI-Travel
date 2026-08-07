package com.ai.travel.service.impl;

import com.ai.travel.config.JwtUtil;
import com.ai.travel.dto.LoginRequest;
import com.ai.travel.dto.LoginResponse;
import com.ai.travel.entity.User;
import com.ai.travel.repository.UserRepository;
import com.ai.travel.service.LoginService;
import com.ai.travel.service.WeChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 登录服务实现
 * <p>
 * 支持两种登录方式：
 * 1. 手机号 + 密码登录（自动注册新用户）
 * 2. 游客模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    private final UserRepository userRepository;
    private final WeChatService weChatService;
    private final JwtUtil jwtUtil;
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginResponse loginByPhone(LoginRequest request) {
        String phone = request.getPhone();
        String password = request.getPassword();

        log.info("手机号登录请求：phone={}", phone);

        // 1. 校验手机号
        if (!StringUtils.hasText(phone)) {
            return LoginResponse.builder()
                    .userId(null)
                    .token(null)
                    .message("手机号不能为空")
                    .build();
        }

        if (!phone.matches("^1[3-9]\\d{9}$")) {
            return LoginResponse.builder()
                    .userId(null)
                    .token(null)
                    .message("手机号格式不正确")
                    .build();
        }

        // 2. 校验密码（接收客户端 SHA256 哈希值，64 位十六进制字符串）
        if (!StringUtils.hasText(password)) {
            return LoginResponse.builder()
                    .userId(null)
                    .token(null)
                    .message("密码不能为空")
                    .build();
        }

        if (password.length() != 64 || !password.matches("^[a-f0-9]{64}$")) {
            log.warn("密码哈希格式异常：length={}", password.length());
            return LoginResponse.builder()
                    .userId(null)
                    .token(null)
                    .message("密码格式错误")
                    .build();
        }

        try {
            // 3. 按手机号查找用户
            User existingUser = userRepository.findByPhone(phone);

            if (existingUser != null) {
                // 3a. 已有用户 → 验证密码
                log.info("手机号 {} 已有用户 userId={}", phone, existingUser.getId());

                boolean needUpdate = false;

                // 验证密码：兼容旧版 SHA256 和新版 BCrypt
                String storedPassword = existingUser.getPassword();
                if (!StringUtils.hasText(storedPassword)) {
                    // 老用户首次使用手机号登录 → 设置 BCrypt 密码
                    log.info("手机号 {} 已有用户无密码，首次设置密码", phone);
                    existingUser.setPassword(passwordEncoder.encode(password));
                    needUpdate = true;
                } else if (isBcryptHash(storedPassword)) {
                    // BCrypt 密码 → 用 BCrypt 匹配
                    if (!passwordEncoder.matches(password, storedPassword)) {
                        log.warn("手机号 {} 密码验证失败", phone);
                        return LoginResponse.builder()
                                .userId(null)
                                .token(null)
                                .message("密码错误")
                                .build();
                    }
                } else {
                    // 旧版 SHA256 密码 → 直接对比（兼容已有用户）
                    if (!password.equals(storedPassword)) {
                        log.warn("手机号 {} 密码验证失败", phone);
                        return LoginResponse.builder()
                                .userId(null)
                                .token(null)
                                .message("密码错误")
                                .build();
                    }
                    // SHA256 验证通过 → 升级为 BCrypt
                    log.info("手机号 {} 密码从 SHA256 升级为 BCrypt", phone);
                    existingUser.setPassword(passwordEncoder.encode(password));
                    needUpdate = true;
                }

                // 更新用户信息（如果有）
                if (StringUtils.hasText(request.getNickname()) && !StringUtils.hasText(existingUser.getNickname())) {
                    existingUser.setNickname(request.getNickname());
                    needUpdate = true;
                }
                if (StringUtils.hasText(request.getAvatar()) && !StringUtils.hasText(existingUser.getAvatar())) {
                    existingUser.setAvatar(request.getAvatar());
                    needUpdate = true;
                }
                // 绑定微信 openId
                if (StringUtils.hasText(request.getWechatCode())) {
                    String openId = weChatService.getOpenId(request.getWechatCode());
                    if (openId != null) {
                        existingUser.setOpenId(openId);
                        needUpdate = true;
                        log.info("手机号 {} 绑定微信 openId：{}", phone, openId);
                    }
                }
                if (needUpdate) {
                    userRepository.updateById(existingUser);
                }

                String token = generateToken(existingUser.getId());

                log.info("手机号 {} 登录成功", phone);

                return LoginResponse.builder()
                        .userId(existingUser.getId())
                        .token(token)
                        .nickname(existingUser.getNickname())
                        .avatar(existingUser.getAvatar())
                        .phone(existingUser.getPhone())
                        .message("登录成功")
                        .build();
            } else {
                // 3b. 新用户 → 自动注册
                log.info("新用户注册：phone={}", phone);

                User user = new User();
                user.setId(UUID.randomUUID().toString().replace("-", ""));
                user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : "用户" + phone.substring(phone.length() - 4));
                user.setAvatar(StringUtils.hasText(request.getAvatar()) ? request.getAvatar() : "");
                user.setGender(0);
                user.setPhone(phone);
                user.setPassword(passwordEncoder.encode(password)); // 存储 BCrypt 哈希

                // 绑定微信 openId
                if (StringUtils.hasText(request.getWechatCode())) {
                    String openId = weChatService.getOpenId(request.getWechatCode());
                    user.setOpenId(openId != null ? openId : "phone_" + phone);
                    log.info("新用户绑定微信 openId：{}", openId);
                } else {
                    user.setOpenId("phone_" + phone);
                }

                userRepository.insert(user);

                String token = generateToken(user.getId());

                log.info("新用户注册成功：userId={}, phone={}", user.getId(), phone);

                return LoginResponse.builder()
                        .userId(user.getId())
                        .token(token)
                        .nickname(user.getNickname())
                        .avatar(user.getAvatar())
                        .phone(phone)
                        .message("注册并登录成功")
                        .build();
            }
        } catch (Exception e) {
            log.error("登录处理异常", e);
            return LoginResponse.builder()
                    .userId(null)
                    .token(null)
                    .message("系统异常：" + e.getMessage())
                    .build();
        }
    }

    @Override
    public LoginResponse guestLogin() {
        String userId = "guest_" + System.currentTimeMillis();
        String token = generateToken(userId);

        User user = new User();
        user.setId(userId);
        user.setOpenId(userId);
        user.setNickname("游客");
        user.setGender(0);
        user.setAvatar("");

        userRepository.insert(user);
        log.info("游客用户已创建：userId={}", userId);

        return LoginResponse.builder()
                .userId(userId)
                .token(token)
                .nickname("游客")
                .avatar("")
                .message("游客模式")
                .build();
    }

    /**
     * 通过微信身份验证重置密码
     *
     * @param phone     手机号
     * @param wechatCode 微信 code
     * @param newPassword 新密码（SHA256）
     * @return 结果消息
     */
    public String resetPassword(String phone, String wechatCode, String newPassword) {
        // 1. 校验输入
        if (!StringUtils.hasText(phone) || !phone.matches("^1[3-9]\\d{9}$")) {
            return "手机号格式不正确";
        }
        if (!StringUtils.hasText(wechatCode)) {
            return "微信验证失败，请重试";
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() != 64) {
            return "密码格式错误";
        }

        // 2. 用 code 换取 openId
        String openId = weChatService.getOpenId(wechatCode);
        if (openId == null) {
            log.warn("微信 code2session 失败");
            return "微信验证失败，请重试";
        }

        // 3. 查找用户
        User user = userRepository.findByPhone(phone);
        if (user == null) {
            return "该手机号未注册";
        }

        // 4. 验证 openId 是否匹配
        if (!StringUtils.hasText(user.getOpenId()) || !user.getOpenId().equals(openId)) {
            log.warn("重置密码 openId 不匹配：phone={}, dbOpenId={}, reqOpenId={}",
                    phone, user.getOpenId(), openId);
            return "身份验证失败，请确认是本人在操作";
        }

        // 5. 更新密码（BCrypt）
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.updateById(user);

        log.info("用户 {} 通过微信验证重置密码成功", phone);
        return "密码重置成功";
    }

    /**
     * 判断字符串是否为 BCrypt 哈希格式（以 $2a$ / $2b$ / $2y$ 开头）
     */
    private boolean isBcryptHash(String password) {
        return password != null && password.startsWith("$2");
    }

    /**
     * 生成 JWT 登录 token
     */
    private String generateToken(String userId) {
        return jwtUtil.generateToken(userId);
    }
}
