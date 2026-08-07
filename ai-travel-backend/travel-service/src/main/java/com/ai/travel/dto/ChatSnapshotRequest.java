package com.ai.travel.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatSnapshotRequest {
    private String title;
    private String phase;
    private List<MessageItem> messages;

    @Data
    public static class MessageItem {
        private String id;
        private String role;
        private String type;
        private String content;
        private Object payload;
        private Long timestamp;
    }
}
