package com.ai.travel.controller;

import com.ai.travel.entity.Message;
import com.ai.travel.dto.ApiResponse;
import com.ai.travel.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("")
    public ApiResponse<List<Message>> getMessages(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        List<Message> messages = messageService.getMessages(userId, type, page, size);
        return ApiResponse.success(messages);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> getUnreadCount(
            @RequestHeader("X-User-Id") String userId) {
        Map<String, Object> unreadCount = messageService.getUnreadCount(userId);
        return ApiResponse.success(unreadCount);
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id) {
        messageService.markAsRead(id, userId);
        return ApiResponse.success(null, "标记已读成功");
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "") String type) {
        messageService.markAllAsRead(userId, type);
        return ApiResponse.success(null, "全部标记已读成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMessage(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id) {
        messageService.deleteMessage(id, userId);
        return ApiResponse.success(null, "删除成功");
    }

    @DeleteMapping("/clear")
    public ApiResponse<Void> clearMessages(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "") String type) {
        messageService.clearMessages(userId, type);
        return ApiResponse.success(null, "清空成功");
    }

    @PostMapping("")
    public ApiResponse<Message> createMessage(@RequestBody Message message) {
        messageService.createMessage(message);
        return ApiResponse.success(message, "创建成功");
    }
}
