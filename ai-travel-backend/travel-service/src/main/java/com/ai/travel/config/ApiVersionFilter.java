package com.ai.travel.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * API 版本号兼容过滤器
 * <p>
 * 将旧路径 /api/xxx 透明重写为 /api/v1/xxx，
 * 前端无需改动即可兼容。
 */
@Component
public class ApiVersionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();

        // /api/xxx（非 /api/v1/）→ 重写为 /api/v1/xxx
        if (uri.startsWith("/api/") && !uri.startsWith("/api/v1/")) {
            String newUri = "/api/v1" + uri.substring(4);
            request = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getRequestURI() { return newUri; }
                @Override
                public String getServletPath() { return newUri; }
            };
        }

        chain.doFilter(request, response);
    }
}
