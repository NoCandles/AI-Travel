package com.ai.travel.controller;

import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.dto.ChatConversationDTO;
import com.ai.travel.dto.ChatHistoryDTO;
import com.ai.travel.dto.ChatResponse;
import com.ai.travel.dto.ChatSendRequest;
import com.ai.travel.dto.ChatSnapshotRequest;
import com.ai.travel.config.RequireLogin;
import com.ai.travel.config.UserContext;
import com.ai.travel.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 对话接口
 * 
 * POST /api/chat/send     - 发送消息，AI解析意图
 * POST /api/chat/confirm  - 确认参数，触发路线生成
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@RequireLogin
public class ChatController {

    private final ChatService chatService;

    /**
     * 发送对话消息
     * AI 解析用户自然语言 →提取行程参数 →返回文本消息 + 参数卡片
     */
    @PostMapping("/send")
    public ApiResponse<ChatResponse> send(@RequestBody ChatSendRequest request) {
        String userId = UserContext.getCurrentUserId();
        log.info("Chat send: userId={}, text={}", userId, request.getText());
        ChatResponse response = chatService.processMessage(userId, request);
        return ApiResponse.success(response);
    }

    /**
     * 确认参数并触发路线生成
     */
    @PostMapping("/confirm")
    public ApiResponse<String> confirm(@RequestBody ChatSendRequest.ConfirmParams params) {
        String userId = UserContext.getCurrentUserId();
        log.info("Chat confirm: userId={}, destination={}, days={}",
                userId, params.getDestination(), params.getDays());
        String tripId = chatService.confirmAndGenerate(userId, params.getConversationId(), params);
        return ApiResponse.success(tripId);
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ChatConversationDTO>> conversations() {
        String userId = UserContext.getCurrentUserId();
        return ApiResponse.success(chatService.listConversations(userId));
    }

    @GetMapping("/history/{conversationId}")
    public ApiResponse<ChatHistoryDTO> history(@PathVariable String conversationId) {
        String userId = UserContext.getCurrentUserId();
        return ApiResponse.success(chatService.getHistory(userId, conversationId));
    }

    @PutMapping("/conversations/{conversationId}/snapshot")
    public ApiResponse<Void> snapshot(@PathVariable String conversationId,
                                      @RequestBody ChatSnapshotRequest request) {
        String userId = UserContext.getCurrentUserId();
        chatService.saveSnapshot(userId, conversationId, request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(@PathVariable String conversationId) {
        String userId = UserContext.getCurrentUserId();
        chatService.deleteConversation(userId, conversationId);
        return ApiResponse.success(null);
    }
}
