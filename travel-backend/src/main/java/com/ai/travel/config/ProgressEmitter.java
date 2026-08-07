package com.ai.travel.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行程生成进度推送中心
 * <p>
 * SSE 控制器创建 emitter 注册到这里，TripGenerationProcessor 推送进度事件。
 */
@Component
public class ProgressEmitter {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String tripId) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时
        emitters.put(tripId, emitter);
        emitter.onCompletion(() -> emitters.remove(tripId));
        emitter.onTimeout(() -> emitters.remove(tripId));
        emitter.onError(e -> emitters.remove(tripId));
        return emitter;
    }

    public void send(String tripId, String event, Object data) {
        SseEmitter emitter = emitters.get(tripId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event)
                        .data(data));
            } catch (Exception e) {
                emitters.remove(tripId);
            }
        }
    }

    public void sendComplete(String tripId) {
        SseEmitter emitter = emitters.get(tripId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("ok"));
                emitter.complete();
            } catch (Exception e) {
                // ignore
            }
            emitters.remove(tripId);
        }
    }

    public void sendError(String tripId, String message) {
        SseEmitter emitter = emitters.get(tripId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(message));
                emitter.completeWithError(new RuntimeException(message));
            } catch (Exception e) {
                // ignore
            }
            emitters.remove(tripId);
        }
    }
}
