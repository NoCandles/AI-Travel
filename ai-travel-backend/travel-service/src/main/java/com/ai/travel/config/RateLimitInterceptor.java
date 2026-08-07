package com.ai.travel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 接口限流拦截器
 * <p>
 * 使用滑动窗口算法，按用户维度限制 AI 生成类接口的调用频率。
 * 防止恶意调用导致高额外部 API 费用。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 限流窗口：每个用户每分钟最大请求数 */
    private static final int MAX_REQUESTS_PER_MINUTE = 3;
    private static final long WINDOW_MS = 60_000;

    /** 用户请求时间戳记录 */
    private final Map<String, long[]> requestTimestamps = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            userId = request.getHeader("userId");
        }
        if (userId == null) {
            // 未认证用户按 IP 限流
            userId = "ip:" + getClientIp(request);
        }

        synchronized (getLock(userId)) {
            long now = System.currentTimeMillis();
            long[] timestamps = requestTimestamps.computeIfAbsent(userId,
                    k -> new long[MAX_REQUESTS_PER_MINUTE]);

            // 清理过期记录
            int validCount = 0;
            for (int i = 0; i < timestamps.length; i++) {
                if (now - timestamps[i] < WINDOW_MS) {
                    validCount++;
                }
            }

            if (validCount >= MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for user: {}, path: {}", userId, request.getRequestURI());
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                return false;
            }

            // 记录本次请求
            timestamps[validCount] = now;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /** 减小锁粒度：每个用户独立锁 */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    private Object getLock(String userId) {
        return locks.computeIfAbsent(userId, k -> new Object());
    }
}
