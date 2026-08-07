package com.ai.travel.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImConversationDTO {
    private String id;
    private String targetUserId;
    private String targetNickname;
    private String targetAvatar;
    private String targetSignature;
    private String lastMessage;
    private String lastMessageType;
    private String lastRouteId;
    private Integer unreadCount;
    private LocalDateTime updatedAt;
}
