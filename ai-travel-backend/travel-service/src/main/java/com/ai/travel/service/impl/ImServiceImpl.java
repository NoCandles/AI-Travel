package com.ai.travel.service.impl;

import com.ai.travel.common.exception.BusinessException;
import com.ai.travel.dto.ImConversationDTO;
import com.ai.travel.dto.ImMessageDTO;
import com.ai.travel.entity.ImConversation;
import com.ai.travel.entity.ImMessage;
import com.ai.travel.entity.TripPublish;
import com.ai.travel.entity.User;
import com.ai.travel.repository.ImConversationRepository;
import com.ai.travel.repository.ImMessageRepository;
import com.ai.travel.repository.TripPublishRepository;
import com.ai.travel.repository.UserRepository;
import com.ai.travel.service.ImService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImServiceImpl implements ImService {

    private final ImConversationRepository conversationRepository;
    private final ImMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final TripPublishRepository tripPublishRepository;

    @Override
    public List<ImConversationDTO> getConversations(String userId) {
        List<ImConversation> conversations = conversationRepository.selectList(
                new LambdaQueryWrapper<ImConversation>()
                        .and(w -> w.eq(ImConversation::getUserAId, userId).or().eq(ImConversation::getUserBId, userId))
                        .orderByDesc(ImConversation::getUpdatedAt));
        return conversations.stream().map(row -> toConversationDTO(row, userId)).collect(Collectors.toList());
    }

    @Override
    public List<ImMessageDTO> getMessages(String userId, String targetUserId, Integer page, Integer size) {
        if (StringUtils.isBlank(targetUserId)) {
            return Collections.emptyList();
        }
        ImConversation conversation = findConversation(userId, targetUserId);
        if (conversation == null) {
            return Collections.emptyList();
        }
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : size;
        List<ImMessage> rows = messageRepository.selectList(
                new LambdaQueryWrapper<ImMessage>()
                        .eq(ImMessage::getConversationId, conversation.getId())
                        .orderByDesc(ImMessage::getCreatedAt)
                        .last("LIMIT " + ((currentPage - 1) * pageSize) + "," + pageSize));
        Collections.reverse(rows);
        markRead(userId, conversation.getId());
        return rows.stream().map(row -> toMessageDTO(row, userId)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ImMessageDTO sendText(String userId, String targetUserId, String content) {
        String text = content == null ? "" : content.trim();
        if (StringUtils.isBlank(text)) {
            throw new BusinessException("消息内容不能为空");
        }
        if (text.length() > 1000) {
            throw new BusinessException("消息内容不能超过1000字");
        }
        return sendMessage(userId, targetUserId, "text", text, null, null, null);
    }

    @Override
    @Transactional
    public ImMessageDTO sendRoute(String userId, String targetUserId, String routeId) {
        if (StringUtils.isBlank(routeId)) {
            throw new BusinessException("路线不存在");
        }
        TripPublish route = tripPublishRepository.selectById(routeId);
        if (route == null || (route.getStatus() != null && route.getStatus() != 1)) {
            throw new BusinessException("路线不存在或未公开");
        }
        String title = StringUtils.defaultIfBlank(route.getTitle(), "分享了一条旅行路线");
        return sendMessage(userId, targetUserId, "route", "分享路线：" + title, route.getId(), title, route.getCoverImage());
    }

    @Override
    @Transactional
    public void markRead(String userId, String conversationId) {
        if (StringUtils.isBlank(conversationId)) return;
        ImConversation conversation = conversationRepository.selectById(conversationId);
        if (conversation == null) return;
        if (Objects.equals(userId, conversation.getUserAId())) {
            conversation.setUnreadA(0);
        } else if (Objects.equals(userId, conversation.getUserBId())) {
            conversation.setUnreadB(0);
        } else {
            return;
        }
        conversationRepository.updateById(conversation);
        messageRepository.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ImMessage>()
                .eq(ImMessage::getConversationId, conversationId)
                .eq(ImMessage::getReceiverId, userId)
                .set(ImMessage::getIsRead, 1));
    }

    @Override
    public Long getUnreadCount(String userId) {
        List<ImConversation> conversations = conversationRepository.selectList(
                new LambdaQueryWrapper<ImConversation>()
                        .and(w -> w.eq(ImConversation::getUserAId, userId).or().eq(ImConversation::getUserBId, userId)));
        return conversations.stream()
                .mapToLong(row -> Objects.equals(row.getUserAId(), userId)
                        ? safeInt(row.getUnreadA())
                        : safeInt(row.getUnreadB()))
                .sum();
    }

    private ImMessageDTO sendMessage(String userId, String targetUserId, String type, String content,
                                     String routeId, String routeTitle, String routeCover) {
        validateParticipants(userId, targetUserId);
        ImConversation conversation = getOrCreateConversation(userId, targetUserId);

        ImMessage message = new ImMessage();
        message.setConversationId(conversation.getId());
        message.setSenderId(userId);
        message.setReceiverId(targetUserId);
        message.setType(type);
        message.setContent(content);
        message.setRouteId(routeId);
        message.setRouteTitle(routeTitle);
        message.setRouteCover(routeCover);
        message.setIsRead(0);
        messageRepository.insert(message);

        conversation.setLastMessage(content);
        conversation.setLastMessageType(type);
        conversation.setLastRouteId(routeId);
        if (Objects.equals(targetUserId, conversation.getUserAId())) {
            conversation.setUnreadA(safeInt(conversation.getUnreadA()) + 1);
        } else {
            conversation.setUnreadB(safeInt(conversation.getUnreadB()) + 1);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.updateById(conversation);

        return toMessageDTO(message, userId);
    }

    private void validateParticipants(String userId, String targetUserId) {
        if (StringUtils.isBlank(targetUserId)) {
            throw new BusinessException("接收人不能为空");
        }
        if (Objects.equals(userId, targetUserId)) {
            throw new BusinessException("不能给自己发私信");
        }
        if (userRepository.selectById(targetUserId) == null) {
            throw new BusinessException("用户不存在");
        }
    }

    private ImConversation getOrCreateConversation(String userId, String targetUserId) {
        ImConversation conversation = findConversation(userId, targetUserId);
        if (conversation != null) return conversation;
        String userA = userId.compareTo(targetUserId) <= 0 ? userId : targetUserId;
        String userB = userId.compareTo(targetUserId) <= 0 ? targetUserId : userId;
        conversation = new ImConversation();
        conversation.setUserAId(userA);
        conversation.setUserBId(userB);
        conversation.setUnreadA(0);
        conversation.setUnreadB(0);
        conversationRepository.insert(conversation);
        return conversation;
    }

    private ImConversation findConversation(String userId, String targetUserId) {
        String userA = userId.compareTo(targetUserId) <= 0 ? userId : targetUserId;
        String userB = userId.compareTo(targetUserId) <= 0 ? targetUserId : userId;
        return conversationRepository.selectOne(new LambdaQueryWrapper<ImConversation>()
                .eq(ImConversation::getUserAId, userA)
                .eq(ImConversation::getUserBId, userB)
                .last("LIMIT 1"));
    }

    private ImConversationDTO toConversationDTO(ImConversation row, String userId) {
        String targetUserId = Objects.equals(row.getUserAId(), userId) ? row.getUserBId() : row.getUserAId();
        User target = userRepository.selectById(targetUserId);
        ImConversationDTO dto = new ImConversationDTO();
        dto.setId(row.getId());
        dto.setTargetUserId(targetUserId);
        dto.setTargetNickname(target != null ? target.getNickname() : "旅行家");
        dto.setTargetAvatar(target != null ? target.getAvatar() : "");
        dto.setTargetSignature(target != null ? target.getSignature() : "");
        dto.setLastMessage(row.getLastMessage());
        dto.setLastMessageType(row.getLastMessageType());
        dto.setLastRouteId(row.getLastRouteId());
        dto.setUnreadCount(Objects.equals(row.getUserAId(), userId) ? safeInt(row.getUnreadA()) : safeInt(row.getUnreadB()));
        dto.setUpdatedAt(row.getUpdatedAt());
        return dto;
    }

    private ImMessageDTO toMessageDTO(ImMessage row, String userId) {
        ImMessageDTO dto = new ImMessageDTO();
        dto.setId(row.getId());
        dto.setConversationId(row.getConversationId());
        dto.setSenderId(row.getSenderId());
        dto.setReceiverId(row.getReceiverId());
        dto.setType(row.getType());
        dto.setContent(row.getContent());
        dto.setRouteId(row.getRouteId());
        dto.setRouteTitle(row.getRouteTitle());
        dto.setRouteCover(row.getRouteCover());
        dto.setIsMine(Objects.equals(row.getSenderId(), userId));
        dto.setIsRead(row.getIsRead() != null && row.getIsRead() == 1);
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
