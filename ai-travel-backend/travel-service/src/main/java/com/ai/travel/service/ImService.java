package com.ai.travel.service;

import com.ai.travel.dto.ImConversationDTO;
import com.ai.travel.dto.ImMessageDTO;

import java.util.List;

public interface ImService {

    List<ImConversationDTO> getConversations(String userId);

    List<ImMessageDTO> getMessages(String userId, String targetUserId, Integer page, Integer size);

    ImMessageDTO sendText(String userId, String targetUserId, String content);

    ImMessageDTO sendRoute(String userId, String targetUserId, String routeId);

    void markRead(String userId, String conversationId);

    Long getUnreadCount(String userId);
}
