package com.ai.travel.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 用户身份拦截器
 * <p>
 * 从 X-Token 头中提取并校验 JWT Token：
 * <ul>
 *   <li>Token 有效 → 从 Token 中提取 userId 设置到 UserContext</li>
 *   <li>Token 无效/过期 → 返回 401 未授权</li>
 *   <li>无 Token → 允许匿名访问（UserContext 设为 anonymous_user）</li>
 * </ul>
 * 登录和健康检查接口不校验 Token。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    /** 不需要校验 Token 的路径前缀 */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/guest",
            "/api/auth/reset-password",
            "/api/health"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 请求直接放行（CORS 预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 公开路径直接放行
        String path = request.getRequestURI();
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                UserContext.setCurrentUserId("anonymous_user");
                return true;
            }
        }

        String token = request.getHeader("X-Token");

        // 无 Token → 允许匿名访问
        if (token == null || token.trim().isEmpty()) {
            log.debug("无 Token，允许匿名访问：{}", path);
            UserContext.setCurrentUserId("anonymous_user");
            return true;
        }

        // 校验 JWT Token
        Claims claims = jwtUtil.validateToken(token);
        if (claims == null) {
            log.warn("Token 无效或已过期，拒绝访问：{}", path);
            sendUnauthorized(response, "Token 无效或已过期，请重新登录");
            return false;
        }

        // Token 有效 → 从 Token 中提取 userId（忽略客户端传入的 X-User-Id）
        String userId = claims.getSubject();
        UserContext.setCurrentUserId(userId);
        log.debug("Token 校验通过：userId={}, 请求路径：{}", userId, path);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.clear();
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
