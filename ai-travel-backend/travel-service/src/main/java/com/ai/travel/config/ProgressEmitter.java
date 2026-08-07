package com.ai.travel.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行程生成进度推送中心（SSE + WebSocket 双通道）
 * <p>
 * SSE 控制器 / TripGenerationProcessor 均可通过此类推送进度事件。
 * v2.0: 新增 WebSocket 通道支持，前端通过 ws:// 直连获取实时推送。
 * v3.0: 新增聊天 AI 优化推送支持，通过 /ws/chat/msg/{convId} 推送优化参数。
 */
@Slf4j
@Component
public class ProgressEmitter {

    // ── SSE 通道（已有） ──
    private final Map<String, org.springframework.web.servlet.mvc.method.annotation.SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    // ── WebSocket 通道（已有） ──
    private final Map<String, WebSocketSession> wsSessions = new ConcurrentHashMap<>();
    
    // 🚀 新增：聊天 WebSocket 通道（用于推送 AI 优化参数）
    private final Map<String, WebSocketSession> chatWsSessions = new ConcurrentHashMap<>();

    // ========== SSE API（兼容已有调用） ==========

    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter register(String tripId) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120_000L);
        sseEmitters.put(tripId, emitter);
        emitter.onCompletion(() -> sseEmitters.remove(tripId));
        emitter.onTimeout(() -> sseEmitters.remove(tripId));
        emitter.onError(e -> sseEmitters.remove(tripId));
        return emitter;
    }

    // ========== WebSocket API（已有） ==========

    /**
     * 注册 WebSocket 会话（行程生成进度）
     */
    public void registerWs(String tripId, WebSocketSession session) {
        wsSessions.put(tripId, session);
    }

    /**
     * 移除 WebSocket 会话（行程生成进度）
     */
    public void removeWs(String tripId) {
        wsSessions.remove(tripId);
    }

    // ========== 🚀 新增：聊天 WebSocket API ==========

    /**
     * 注册聊天 WebSocket 会话（AI 优化参数推送）
     */
    public void registerChatWs(String conversationId, WebSocketSession session) {
        chatWsSessions.put(conversationId, session);
        log.info("📨 注册聊天 WebSocket: conversationId={}, sessionId={}", conversationId, session.getId());
    }

    /**
     * 移除聊天 WebSocket 会话
     */
    public void removeChatWs(String conversationId) {
        chatWsSessions.remove(conversationId);
        log.info("📨 移除聊天 WebSocket: conversationId={}", conversationId);
    }

    // ========== 统一推送（双通道广播） ==========

    /**
     * 推送进度/消息事件到 SSE + WebSocket
     */
    public void send(String tripId, String event, Object data) {
        // SSE 通道
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter sseEmitter = sseEmitters.get(tripId);
        if (sseEmitter != null) {
            try {
                sseEmitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name(event)
                        .data(data));
            } catch (Exception e) {
                sseEmitters.remove(tripId);
            }
        }

        // WebSocket 通道（行程生成进度）
        WebSocketSession wsSession = wsSessions.get(tripId);
        if (wsSession != null && wsSession.isOpen()) {
            try {
                String json;
                if (data instanceof String) {
                    json = (String) data;
                } else {
                    json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
                }
                // 封装为统一格式
                String payload = String.format("{\"type\":\"%s\",\"data\":%s}", event, json);
                synchronized (wsSession) {
                    wsSession.sendMessage(new org.springframework.web.socket.TextMessage(payload));
                }
            } catch (Exception e) {
                wsSessions.remove(tripId);
            }
        }
    }

    /**
     * 🚀 推送聊天 AI 优化结果到聊天 WebSocket
     * @param conversationId 对话ID
     * @param event 事件类型（如 "param_optimized"）
     * @param data 推送数据
     */
    public void sendToChat(String conversationId, String event, Object data) {
        WebSocketSession wsSession = chatWsSessions.get(conversationId);
        if (wsSession != null && wsSession.isOpen()) {
            try {
                String json = (data instanceof String) 
                        ? (String) data 
                        : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
                
                String payload = String.format("{\"type\":\"%s\",\"data\":%s}", event, json);
                synchronized (wsSession) {
                    wsSession.sendMessage(new org.springframework.web.socket.TextMessage(payload));
                    log.info("📨 WebSocket 推送成功: conversationId={}, event={}", conversationId, event);
                }
            } catch (Exception e) {
                log.warn("⚠️ 聊天 WebSocket 推送失败: conversationId={}, error={}", conversationId, e.getMessage());
                chatWsSessions.remove(conversationId);
            }
        } else {
            log.warn("⚠️ 聊天 WebSocket 会话不存在: conversationId={}", conversationId);
        }
    }

    /**
     * 推送完成事件
     */
    public void sendComplete(String tripId) {
        // SSE
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter sseEmitter = sseEmitters.get(tripId);
        if (sseEmitter != null) {
            try {
                sseEmitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("complete").data("ok"));
                sseEmitter.complete();
            } catch (Exception e) { /* ignore */ }
            sseEmitters.remove(tripId);
        }

        // WebSocket
        WebSocketSession wsSession = wsSessions.get(tripId);
        if (wsSession != null && wsSession.isOpen()) {
            try {
                synchronized (wsSession) {
                    wsSession.sendMessage(new org.springframework.web.socket.TextMessage(
                            "{\"type\":\"done\",\"data\":\"ok\"}"));
                    wsSession.close();
                }
            } catch (Exception e) { /* ignore */ }
        }
        wsSessions.remove(tripId);
    }

    /**
     * 推送错误事件
     */
    public void sendError(String tripId, String message) {
        // SSE
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter sseEmitter = sseEmitters.get(tripId);
        if (sseEmitter != null) {
            try {
                sseEmitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("error").data(message));
                sseEmitter.completeWithError(new RuntimeException(message));
            } catch (Exception e) { /* ignore */ }
            sseEmitters.remove(tripId);
        }

        // WebSocket
        WebSocketSession wsSession = wsSessions.get(tripId);
        if (wsSession != null && wsSession.isOpen()) {
            try {
                synchronized (wsSession) {
                    wsSession.sendMessage(new org.springframework.web.socket.TextMessage(
                            String.format("{\"type\":\"error\",\"data\":{\"message\":\"%s\"}}",
                                    message.replace("\"", "\\\""))));
                    wsSession.close();
                }
            } catch (Exception e) { /* ignore */ }
        }
        wsSessions.remove(tripId);
    }
}
