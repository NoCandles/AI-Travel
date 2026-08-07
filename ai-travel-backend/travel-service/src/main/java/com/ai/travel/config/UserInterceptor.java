package com.ai.travel.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import org.springframework.web.method.HandlerMethod;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * 用户身份拦截器
 * <p>
 * 从 X-Token 头中提取并校验 JWT Token：
 * <ul>
 *   <li>Token 有效 → 从 Token 中提取 userId 设置到 UserContext</li>
 *   <li>Token 无效/过期 → 降级为匿名访问（公开接口可正常访问，需登录接口由 @RequireLogin 拦截）</li>
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

        // 无 Token → 允许匿名访问（但需检查 @RequireLogin）
        if (token == null || token.trim().isEmpty()) {
            log.debug("无 Token，允许匿名访问：{}", path);
            UserContext.setCurrentUserId("anonymous_user");
            return checkRequireLogin(handler, response, path);
        }

        // 校验 JWT Token
        Claims claims = jwtUtil.validateToken(token);
        if (claims == null) {
            // Token 无效/过期 → 降级为匿名访问，由 @RequireLogin 决定是否拦截
            // 这样公开接口（行程详情、评论列表等）即使用户 token 过期也能正常访问
            log.debug("Token 无效或已过期，降级为匿名访问：{}", path);
            UserContext.setCurrentUserId("anonymous_user");
            return checkRequireLogin(handler, response, path);
        }

        // Token 有效 → 从 Token 中提取 userId（忽略客户端传入的 X-User-Id）
        String userId = claims.getSubject();
        UserContext.setCurrentUserId(userId);
        log.debug("Token 校验通过：userId={}, 请求路径：{}", userId, path);

        // 检查是否需要登录
        return checkRequireLogin(handler, response, path);
    }

    /**
     * 检查接口是否标注了 @RequireLogin，若是且用户未登录则返回 401
     */
    private boolean checkRequireLogin(Object handler, HttpServletResponse response, String path) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireLogin methodAnnotation = handlerMethod.getMethodAnnotation(RequireLogin.class);
        RequireLogin classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireLogin.class);

        if (methodAnnotation == null && classAnnotation == null) {
            return true;
        }

        String userId = UserContext.getCurrentUserId();
        if (userId == null || "anonymous_user".equals(userId)) {
            log.warn("未登录用户访问需认证接口：{}", path);
            sendUnauthorized(response, "请先登录");
            return false;
        }
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
