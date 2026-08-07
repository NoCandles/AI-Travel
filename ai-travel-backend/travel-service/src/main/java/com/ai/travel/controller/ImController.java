package com.ai.travel.controller;

import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.config.RequireLogin;
import com.ai.travel.config.UserContext;
import com.ai.travel.dto.ImConversationDTO;
import com.ai.travel.dto.ImMessageDTO;
import com.ai.travel.dto.ImSendMessageRequest;
import com.ai.travel.service.ImService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/im")
@RequiredArgsConstructor
@RequireLogin
public class ImController {

    private final ImService imService;

    @GetMapping("/conversations")
    public ApiResponse<List<ImConversationDTO>> getConversations() {
        return ApiResponse.success(imService.getConversations(UserContext.getCurrentUserId()));
    }

    @GetMapping("/messages")
    public ApiResponse<List<ImMessageDTO>> getMessages(
            @RequestParam String targetUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(imService.getMessages(UserContext.getCurrentUserId(), targetUserId, page, size));
    }

    @PostMapping("/messages")
    public ApiResponse<ImMessageDTO> sendText(@RequestBody ImSendMessageRequest request) {
        return ApiResponse.success(imService.sendText(
                UserContext.getCurrentUserId(),
                request.getTargetUserId(),
                request.getContent()));
    }

    @PostMapping("/messages/route")
    public ApiResponse<ImMessageDTO> sendRoute(@RequestBody ImSendMessageRequest request) {
        return ApiResponse.success(imService.sendRoute(
                UserContext.getCurrentUserId(),
                request.getTargetUserId(),
                request.getRouteId()));
    }

    @PutMapping("/conversations/{conversationId}/read")
    public ApiResponse<Void> markRead(@PathVariable String conversationId) {
        imService.markRead(UserContext.getCurrentUserId(), conversationId);
        return ApiResponse.success(null);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> getUnreadCount() {
        return ApiResponse.success(Map.of("total", imService.getUnreadCount(UserContext.getCurrentUserId())));
    }
}
