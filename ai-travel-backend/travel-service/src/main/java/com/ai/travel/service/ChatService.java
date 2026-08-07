package com.ai.travel.service;

import com.ai.travel.dto.ChatResponse;
import com.ai.travel.dto.ChatConversationDTO;
import com.ai.travel.dto.ChatHistoryDTO;
import com.ai.travel.dto.ChatSnapshotRequest;
import com.ai.travel.dto.ChatSendRequest;

import java.util.List;

/**
 * AI 对话服务
 * 
 * 功能：
 * 1. 解析用户自然语言 → 提取行程参数
 * 2. 多轮对话上下文管理
 * 3. 参数补全 & 追问逻辑
 */
public interface ChatService {

    /**
     * 处理用户对话消息
     *
     * @param userId  用户ID
     * @param request 对话请求（包含用户输入 + 上下文）
     * @return AI 响应（文本消息 + 解析出的参数）
     */
    ChatResponse processMessage(String userId, ChatSendRequest request);

    /**
     * 确认参数并触发路线生成
     *
     * @param userId         用户ID
     * @param conversationId 对话会话ID
     * @param params         确认的行程参数
     * @return 行程ID
     */
    String confirmAndGenerate(String userId, String conversationId, ChatSendRequest.ConfirmParams params);

    List<ChatConversationDTO> listConversations(String userId);

    ChatHistoryDTO getHistory(String userId, String conversationId);

    void deleteConversation(String userId, String conversationId);

    void saveSnapshot(String userId, String conversationId, ChatSnapshotRequest request);
}
