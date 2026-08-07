package com.ai.travel.service;

import com.ai.travel.entity.Message;
import com.ai.travel.entity.User;
import com.ai.travel.repository.MessageRepository;
import com.ai.travel.repository.UserRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public List<Message> getMessages(String userId, String type, Integer page, Integer size) {
        int offset = (page - 1) * size;
        List<Message> messages = messageRepository.findByUserIdAndType(userId, type, offset, size);

        // 填充发送者信息
        for (Message msg : messages) {
            if (msg.getSenderId() != null && !msg.getSenderId().isEmpty()) {
                User sender = userRepository.selectById(msg.getSenderId());
                if (sender != null) {
                    msg.setSenderName(sender.getNickname());
                    msg.setSenderAvatar(sender.getAvatar());
                }
            } else {
                msg.setSenderName("系统通知");
            }
        }
        return messages;
    }

    public Map<String, Object> getUnreadCount(String userId) {
        Map<String, Object> result = new HashMap<>();
        long commentCount = messageRepository.countByUserIdAndType(userId, "comment", 0);
        long replyCount = messageRepository.countByUserIdAndType(userId, "reply", 0);
        long likeCount = messageRepository.countByUserIdAndType(userId, "like", 0);
        long followCount = messageRepository.countByUserIdAndType(userId, "follow", 0);
        long systemCount = messageRepository.countByUserIdAndType(userId, "system", 0);
        long interactionCount = commentCount + replyCount + likeCount + followCount;
        long total = interactionCount + systemCount;

        result.put("comment", commentCount);
        result.put("reply", replyCount);
        result.put("like", likeCount);
        result.put("follow", followCount);
        result.put("interaction", interactionCount);
        result.put("system", systemCount);
        result.put("total", total);
        return result;
    }

    public void markAsRead(Long id, String userId) {
        messageRepository.markAsRead(id, userId);
    }

    public void markAllAsRead(String userId, String type) {
        messageRepository.markAllAsRead(userId, type);
    }

    public void deleteMessage(Long id, String userId) {
        Message message = messageRepository.selectById(id);
        if (message != null && message.getUserId().equals(userId)) {
            messageRepository.deleteById(id);
        }
    }

    public void clearMessages(String userId, String type) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getUserId, userId);
        if (type != null && !type.isEmpty()) {
            if ("interaction".equals(type)) {
                wrapper.in(Message::getType, "comment", "reply", "like", "follow");
            } else {
                wrapper.eq(Message::getType, type);
            }
        }
        messageRepository.delete(wrapper);
    }

    public void createMessage(Message message) {
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        messageRepository.insert(message);
    }
}
