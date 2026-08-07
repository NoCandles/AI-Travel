package com.ai.travel.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatHistoryDTO {
    private String conversationId;
    private ChatConversationDTO conversation;
    private List<MessageItem> messages;

    @Data
    @Builder
    public static class MessageItem {
        private String id;
        private String role;
        private String type;
        private String content;
        private Object payload;
        private Long timestamp;
    }
}
