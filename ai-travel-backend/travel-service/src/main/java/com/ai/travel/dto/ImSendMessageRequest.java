package com.ai.travel.dto;

import lombok.Data;

@Data
public class ImSendMessageRequest {
    private String targetUserId;
    private String content;
    private String routeId;
}
