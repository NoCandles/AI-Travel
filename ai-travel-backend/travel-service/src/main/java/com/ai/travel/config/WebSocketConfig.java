package com.ai.travel.config;

import com.ai.travel.handler.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * <p>
 * 注册以下端点（均需认证）：
 *   /ws/chat/{tripId}       - 行程生成进度推送
 *   /ws/chat/{convId}?channel=conversation - 聊天 AI 优化推送
 * <p>
 * 客户端通过 wx.cloud.connectContainer({ service, path: "/ws/chat/{tripId}?token=xxx" }) 连接。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WebSocketAuthInterceptor authInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 行程生成进度推送
        registry.addHandler(chatWebSocketHandler, "/ws/chat/{tripId}")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");

        // 聊天 AI 优化推送
        registry.addHandler(chatWebSocketHandler, "/ws/chat/msg/{convId}")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
