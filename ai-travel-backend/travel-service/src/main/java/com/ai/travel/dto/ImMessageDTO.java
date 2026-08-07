package com.ai.travel.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImMessageDTO {
    private Long id;
    private String conversationId;
    private String senderId;
    private String receiverId;
    private String type;
    private String content;
    private String routeId;
    private String routeTitle;
    private String routeCover;
    private Boolean isMine;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
