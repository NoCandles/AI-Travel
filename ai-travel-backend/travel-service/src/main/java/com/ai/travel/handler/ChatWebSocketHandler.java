package com.ai.travel.handler;

import com.ai.travel.config.ProgressEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * 聊天 WebSocket 处理器
 * <p>
 * 路径: 
 *   /ws/chat/{tripId}       - 行程生成进度推送
 *   /ws/chat/msg/{convId}  - 聊天 AI 优化推送
 * <p>
 * 客户端连接后注册到 ProgressEmitter，服务端异步推送：
 *   行程生成: {"type":"progress","data":{"stage":"start","message":"AI 开始规划路线..."}}
 *   聊天优化: {"type":"param_optimized","data":{"text":"...","params":{...}}}
 *   {"type":"done","data":"ok"}
 *   {"type":"error","data":{"message":"..."}}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ProgressEmitter progressEmitter;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String tripId = extractTripId(session);
        String conversationId = extractConversationId(session);
        
        if (tripId == null && conversationId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 注册到 ProgressEmitter
        if (tripId != null) {
            progressEmitter.registerWs(tripId, session);
            log.info("🔌 WebSocket 已连接(行程): tripId={}, sessionId={}", tripId, session.getId());
        }
        
        if (conversationId != null) {
            progressEmitter.registerChatWs(conversationId, session);
            log.info("🔌 WebSocket 已连接(聊天): conversationId={}, sessionId={}", conversationId, session.getId());
        }

        // 发送连接确认
        sendJson(session, Map.of(
                "type", "connected",
                "data", Map.of(
                        "tripId", tripId != null ? tripId : "",
                        "conversationId", conversationId != null ? conversationId : ""
                )
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String tripId = extractTripId(session);
        String conversationId = extractConversationId(session);
        String payload = message.getPayload();
        log.debug("📨 WS 收到消息: tripId={}, convId={}, payload={}", tripId, conversationId, payload);

        // 客户端可以发送 ping/pong 保持连接
        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");
            if ("ping".equals(type)) {
                sendJson(session, Map.of("type", "pong", "data", Map.of()));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String tripId = extractTripId(session);
        String conversationId = extractConversationId(session);
        
        if (tripId != null) {
            progressEmitter.removeWs(tripId);
        }
        if (conversationId != null) {
            progressEmitter.removeChatWs(conversationId);
        }
        
        log.info("🔌 WebSocket 已断开: tripId={}, convId={}, status={}", tripId, conversationId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String tripId = extractTripId(session);
        String conversationId = extractConversationId(session);
        
        log.warn("⚠️ WebSocket 传输错误: tripId={}, convId={}, error={}", tripId, conversationId, exception.getMessage());
        
        if (tripId != null) {
            progressEmitter.removeWs(tripId);
        }
        if (conversationId != null) {
            progressEmitter.removeChatWs(conversationId);
        }
        
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception e) {
            // ignore
        }
    }

    // ── 工具方法 ──

    private String extractTripId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String path = uri.getPath();
        if (path == null) return null;
        if (isConversationChannel(uri)) {
            return null; // 这是聊天路径，不是行程路径
        }
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return parts[parts.length - 1];
        }
        return null;
    }

    private String extractConversationId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String path = uri.getPath();
        if (path == null) return null;
        if (path.contains("/msg/")) {
            String[] parts = path.split("/msg/");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        if (isConversationChannel(uri)) {
            String[] parts = path.split("/");
            if (parts.length >= 3) {
                return parts[parts.length - 1];
            }
        }
        return null;
    }

    private boolean isConversationChannel(URI uri) {
        if (uri.getPath() != null && uri.getPath().contains("/msg/")) {
            return true;
        }
        return "conversation".equals(UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("channel"));
    }

    private void sendJson(WebSocketSession session, Object data) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
                }
            }
        } catch (Exception e) {
            log.warn("WS 发送失败: {}", e.getMessage());
        }
    }
}
