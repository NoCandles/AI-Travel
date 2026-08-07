package com.ai.travel.controller;

import com.ai.travel.entity.Message;
import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.config.RequireLogin;
import com.ai.travel.config.UserContext;
import com.ai.travel.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@RequireLogin
public class MessageController {

    private final MessageService messageService;

    @GetMapping("")
    public ApiResponse<List<Message>> getMessages(
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        String userId = UserContext.getCurrentUserId();
        List<Message> messages = messageService.getMessages(userId, type, page, size);
        return ApiResponse.success(messages);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> getUnreadCount() {
        String userId = UserContext.getCurrentUserId();
        Map<String, Object> unreadCount = messageService.getUnreadCount(userId);
        return ApiResponse.success(unreadCount);
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        String userId = UserContext.getCurrentUserId();
        messageService.markAsRead(id, userId);
        return ApiResponse.success(null, "标记已读成功");
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@RequestParam(defaultValue = "") String type) {
        String userId = UserContext.getCurrentUserId();
        messageService.markAllAsRead(userId, type);
        return ApiResponse.success(null, "全部标记已读成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMessage(@PathVariable Long id) {
        String userId = UserContext.getCurrentUserId();
        messageService.deleteMessage(id, userId);
        return ApiResponse.success(null, "删除成功");
    }

    @DeleteMapping("/clear")
    public ApiResponse<Void> clearMessages(@RequestParam(defaultValue = "") String type) {
        String userId = UserContext.getCurrentUserId();
        messageService.clearMessages(userId, type);
        return ApiResponse.success(null, "清空成功");
    }

    @PostMapping("")
    public ApiResponse<Message> createMessage(@RequestBody Message message) {
        messageService.createMessage(message);
        return ApiResponse.success(message);
    }
}
