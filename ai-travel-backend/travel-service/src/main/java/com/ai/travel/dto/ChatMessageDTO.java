package com.ai.travel.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 聊天消息 DTO
 * 支持多种消息类型：text / param_card / progress / trip_card / hotel_card / map_card
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    /** 消息唯一标识 */
    private String id;

    /** 发送者角色：user / assistant */
    private String role;

    /** 消息类型 */
    private String type;

    /** 文本内容（type=text 时） */
    private String content;

    /** 结构化数据（type=param_card, trip_card, hotel_card 等时） */
    private Object payload;

    /** 时间戳 */
    private Long timestamp;

    // ─── 常用工厂方法 ───

    public static ChatMessageDTO userText(String content) {
        return ChatMessageDTO.builder()
                .role("user")
                .type("text")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ChatMessageDTO assistantText(String content) {
        return ChatMessageDTO.builder()
                .role("assistant")
                .type("text")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ChatMessageDTO paramCard(Map<String, Object> params) {
        return ChatMessageDTO.builder()
                .role("assistant")
                .type("param_card")
                .content("确认行程参数")
                .payload(params)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ChatMessageDTO progress(int percent, String label, List<String> steps) {
        Map<String, Object> payload = Map.of(
                "percent", percent,
                "currentLabel", label,
                "stepLabels", steps
        );
        return ChatMessageDTO.builder()
                .role("assistant")
                .type("progress")
                .content("")
                .payload(payload)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
