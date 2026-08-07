package com.ai.travel.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ai.travel.dto.*;
import com.ai.travel.entity.*;
import com.ai.travel.repository.*;
import com.ai.travel.service.MessageService;
import com.ai.travel.service.PointsService;
import com.ai.travel.service.SensitiveWordService;
import com.ai.travel.service.SocialService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialServiceImpl implements SocialService {

    private final TripPublishRepository tripPublishRepository;
    private final TripPublishImageRepository tripPublishImageRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TripDayRepository tripDayRepository;
    private final TripSpotRepository tripSpotRepository;
    private final CommentRepository commentRepository;
    private final FollowRelationRepository followRelationRepository;
    private final LikeRecordRepository likeRecordRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final PointsService pointsService;
    private final SensitiveWordService sensitiveWordService;

    @Override
    public List<PublishDTO> getPublishedTrips(String category, String keyword, int page, int size, String currentUserId, String city, Integer days) {
        LambdaQueryWrapper<TripPublish> wrapper = new LambdaQueryWrapper<TripPublish>()
                .eq(TripPublish::getStatus, 1); // 已发布

        // 真实注册用户优先展示：user_id 不以 mock-/seed-/guest_ 开头的视为真实用户
        // （真实用户 ID 形如 51cdc6dee3404aba8c7005ebce0e3fdf；假数据前缀 mock-/seed-/guest_）
        String realUserFirst = "(CASE WHEN user_id LIKE 'mock-%' OR user_id LIKE 'seed-%' OR user_id LIKE 'guest_%' THEN 0 ELSE 1 END) DESC";

        // 根据分类排序（真实用户始终排在最前面）
        String orderBySql;
        if ("recommend".equals(category)) {
            // 热门推荐：真实用户优先 + 按点赞数 + 评论数综合排序
            orderBySql = realUserFirst + ", like_count DESC, comment_count DESC, view_count DESC";
        } else {
            // 默认排序：真实用户优先 + 按时间倒序
            orderBySql = realUserFirst + ", created_at DESC";
        }

        // 关键词搜索
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(TripPublish::getTitle, keyword)
                    .or().like(TripPublish::getLocation, keyword)
                    .or().like(TripPublish::getTags, keyword));
        }

        // 多城市筛选（逗号分隔的城市名称列表，例如 "北京,上海,杭州"）
        if (StringUtils.isNotBlank(city)) {
            String[] cityArr = city.split(",");
            if (cityArr.length == 1) {
                wrapper.like(TripPublish::getLocation, cityArr[0].trim());
            } else if (cityArr.length > 1) {
                wrapper.and(w -> {
                    for (int i = 0; i < cityArr.length; i++) {
                        String c = cityArr[i].trim();
                        if (StringUtils.isNotBlank(c)) {
                            if (i == 0) {
                                w.like(TripPublish::getLocation, c);
                            } else {
                                w.or().like(TripPublish::getLocation, c);
                            }
                        }
                    }
                });
            }
        }

        // 天数筛选
        if (days != null && days > 0) {
            wrapper.eq(TripPublish::getDays, days);
        }

        // 真实用户优先 + 原有排序 + 分页（注意：.last() 会整体追加到 SQL 末尾）
        wrapper.last("ORDER BY " + orderBySql + " LIMIT " + (page - 1) * size + "," + size);
        List<TripPublish> list = tripPublishRepository.selectList(wrapper);
        // 批量加载用户信息，避免 N+1
        Map<String, User> userMap = batchLoadUsers(list, TripPublish::getUserId);

        // 批量加载交互状态，避免 N+1（关注、点赞、收藏）
        Set<String> followSet = Collections.emptySet();
        Set<String> likeSet = Collections.emptySet();
        Set<String> favSet = Collections.emptySet();
        if (StringUtils.isNotBlank(currentUserId)) {
            List<String> authorIds = list.stream().map(TripPublish::getUserId).distinct().collect(Collectors.toList());
            List<String> publishIds = list.stream().map(TripPublish::getId).collect(Collectors.toList());
            followSet = followRelationRepository.findFollowingIdsIn(currentUserId, authorIds).stream().collect(Collectors.toSet());
            likeSet = likeRecordRepository.findTargetIdsIn(currentUserId, "publish", "like", publishIds).stream().collect(Collectors.toSet());
            favSet = likeRecordRepository.findTargetIdsIn(currentUserId, "publish", "fav", publishIds).stream().collect(Collectors.toSet());
        }

        final Set<String> finalFollowSet = followSet;
        final Set<String> finalLikeSet = likeSet;
        final Set<String> finalFavSet = favSet;
        return list.stream().map(p -> convertToPublishDTO(p, currentUserId, userMap, finalFollowSet, finalLikeSet, finalFavSet)).collect(Collectors.toList());
    }

    @Override
    public PublishDTO getPublishDetail(String publishId, String currentUserId) {
        TripPublish publish = tripPublishRepository.selectById(publishId);
        if (publish == null) return null;
        // 原子浏览量自增（避免 READ-MODIFY-WRITE 竞态条件）
        tripPublishRepository.incrementViewCount(publishId);

        Set<String> followSet = Collections.emptySet();
        Set<String> likeSet = Collections.emptySet();
        Set<String> favSet = Collections.emptySet();
        if (StringUtils.isNotBlank(currentUserId)) {
            followSet = isFollowed(currentUserId, publish.getUserId())
                    ? Collections.singleton(publish.getUserId())
                    : Collections.emptySet();
            likeSet = likeRecordRepository.exists(currentUserId, publishId, "publish", "like") > 0
                    ? Collections.singleton(publishId)
                    : Collections.emptySet();
            favSet = likeRecordRepository.exists(currentUserId, publishId, "publish", "fav") > 0
                    ? Collections.singleton(publishId)
                    : Collections.emptySet();
        }

        return convertToPublishDTO(publish, currentUserId, loadSingleUser(publish.getUserId()),
                followSet, likeSet, favSet);
    }

    @Override
    public PublishDTO getPublishDraft(String tripPlanId, String userId) {
        TripPlan tripPlan = tripPlanRepository.selectById(tripPlanId);
        if (tripPlan == null) {
            throw new RuntimeException("行程不存在");
        }
        if (!Objects.equals(tripPlan.getUserId(), userId)) {
            throw new RuntimeException("无权发布该行程");
        }

        TripPublish existing = findActivePublishByTripPlanId(tripPlanId);
        if (existing != null) {
            return convertToPublishDTO(existing, userId, loadSingleUser(existing.getUserId()),
                    Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
        }

        PublishDTO draft = new PublishDTO();
        draft.setTripPlanId(tripPlanId);
        draft.setUserId(userId);
        draft.setTitle(StringUtils.isNotBlank(tripPlan.getName()) ? tripPlan.getName() : tripPlan.getDestination() + " trip");
        draft.setDescription(tripPlan.getDescription());
        draft.setContent("");
        draft.setLocation(tripPlan.getDestination());
        List<DayPlanDTO> dayList = buildDayList(tripPlanId);
        int dayCount = dayList == null || dayList.isEmpty() ? 1 : dayList.size();
        draft.setDays(dayCount);
        draft.setNights(Math.max(1, dayCount - 1));
        draft.setCoverImage(resolvePublishCoverImage(tripPlan.getCoverImage(), tripPlanId));
        draft.setTags(buildDefaultTags(tripPlan.getPreferences()));
        draft.setDayList(dayList);
        draft.setImages(buildDraftImages(tripPlanId, draft.getCoverImage()));
        return draft;
    }

    @Override
    @Transactional
    public String createPublish(PublishRequest request, String userId) {
        return createPublishWithImages(request, userId);
    }

    private String createPublishWithImages(PublishRequest request, String userId) {
        validatePublishOwner(request.getTripPlanId(), userId);

        // 敏感词校验
        String matchedTitle = sensitiveWordService.checkText(request.getTitle());
        if (matchedTitle != null) {
            throw new RuntimeException("发布失败，标题包含敏感词");
        }
        String matchedDesc = sensitiveWordService.checkText(request.getDescription());
        if (matchedDesc != null) {
            throw new RuntimeException("发布失败，内容包含敏感词");
        }
        String matchedContent = sensitiveWordService.checkText(request.getContent());
        if (matchedContent != null) {
            throw new RuntimeException("发布失败，正文包含敏感词");
        }
        String coverImage = resolvePublishCoverImage(request.getCoverImage(), request.getTripPlanId());

        // 检查该行程是否已发布过
        TripPublish existingPublish = findActivePublishByTripPlanId(request.getTripPlanId());
        if (existingPublish != null) {
            if (!Objects.equals(existingPublish.getUserId(), userId)) {
                throw new RuntimeException("无权编辑该发布");
            }
            // 已发布过，执行更新
            existingPublish.setTitle(request.getTitle());
            existingPublish.setDescription(request.getDescription());
            existingPublish.setContent(request.getContent());
            existingPublish.setCoverImage(coverImage);
            existingPublish.setLocation(request.getLocation());
            existingPublish.setDays(request.getDays());
            existingPublish.setNights(request.getNights());
            existingPublish.setTags(request.getTags());
            tripPublishRepository.updateById(existingPublish);
            replacePublishImages(existingPublish.getId(), request.getTripPlanId(), userId, request.getImages(), coverImage);
            log.info("更新已发布的行程：publishId={}, tripPlanId={}", existingPublish.getId(), request.getTripPlanId());
            return existingPublish.getId();
        }

        // 首次发布，创建新记录
        TripPublish publish = new TripPublish();
        publish.setUserId(userId);
        publish.setTripPlanId(request.getTripPlanId());
        publish.setTitle(request.getTitle());
        publish.setDescription(request.getDescription());
        publish.setContent(request.getContent());
        publish.setCoverImage(coverImage);
        publish.setLocation(request.getLocation());
        publish.setDays(request.getDays());
        publish.setNights(request.getNights());
        publish.setTags(request.getTags());
        publish.setStatus(1);
        publish.setReviewStatus(1);
        publish.setViewCount(0);
        publish.setLikeCount(0);
        publish.setCommentCount(0);
        publish.setFavCount(0);
        tripPublishRepository.insert(publish);
        replacePublishImages(publish.getId(), request.getTripPlanId(), userId, request.getImages(), coverImage);
        
        // 赚取积分：发布旅行计划 +20 积分
        try {
            pointsService.earnPoints(userId, "publish", publish.getId(), "发布旅行计划");
        } catch (Exception e) {
            log.warn("赚取积分失败: {}", e.getMessage());
        }
        
        return publish.getId();
    }

    @Override
    @Transactional
    public void deletePublish(String publishId, String userId) {
        TripPublish publish = tripPublishRepository.selectById(publishId);
        if (publish != null && publish.getUserId().equals(userId)) {
            tripPublishImageRepository.delete(new LambdaQueryWrapper<TripPublishImage>()
                    .eq(TripPublishImage::getPublishId, publishId));
            tripPublishRepository.deleteById(publishId);
        }
    }

    @Override
    public List<HotTagDTO> getHotTags() {
        // 从已发布的行程中统计标签出现频率
        LambdaQueryWrapper<TripPublish> wrapper = new LambdaQueryWrapper<TripPublish>()
                .eq(TripPublish::getStatus, 1)
                .isNotNull(TripPublish::getTags)
                .orderByDesc(TripPublish::getLikeCount)
                .last("LIMIT 50");
        List<TripPublish> list = tripPublishRepository.selectList(wrapper);

        // 解析标签并统计频率
        Map<String, Integer> tagCount = new LinkedHashMap<>();
        for (TripPublish p : list) {
            if (StringUtils.isNotBlank(p.getTags())) {
                try {
                    List<String> tags = JSON.parseArray(p.getTags(), String.class);
                    for (String tag : tags) {
                        tagCount.merge(tag, 1, Integer::sum);
                    }
                } catch (Exception e) {
                    // 忽略解析错误的标签
                }
            }
        }

        return tagCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    HotTagDTO dto = new HotTagDTO();
                    dto.setName(entry.getKey());
                    dto.setCount(formatCount(entry.getValue()));
                    dto.setImage("https://picsum.photos/id/10" + (new Random().nextInt(9) + 30) + "/80/80");
                    return dto;
                }).collect(Collectors.toList());
    }

    @Override
    public List<CommentDTO> getComments(String publishId, String currentUserId, int page, int size) {
        log.debug("获取评论列表: publishId={}, page={}, size={}", publishId, page, size);

        // 1. 分页查询一级评论
        LambdaQueryWrapper<Comment> rootWrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPublishId, publishId)
                .and(w -> w.isNull(Comment::getParentId).or().eq(Comment::getParentId, ""))
                .orderByAsc(Comment::getCreatedAt)
                .last("LIMIT " + (page - 1) * size + "," + size);
        List<Comment> rootComments = commentRepository.selectList(rootWrapper);

        if (rootComments.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 批量查询这些一级评论的所有子回复
        List<String> rootIds = rootComments.stream().map(Comment::getId).collect(Collectors.toList());
        LambdaQueryWrapper<Comment> replyWrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPublishId, publishId)
                .in(Comment::getParentId, rootIds)
                .orderByAsc(Comment::getCreatedAt);
        List<Comment> allReplies = commentRepository.selectList(replyWrapper);

        // 3. 批量加载所有相关用户信息
        Set<String> userIds = new HashSet<>();
        rootComments.forEach(c -> userIds.add(c.getUserId()));
        allReplies.forEach(c -> userIds.add(c.getUserId()));
        Map<String, User> userMap = batchLoadUsersByIds(userIds);

        // 4. 构建父评论映射（用于回复时显示被回复人）
        Map<String, Comment> parentMap = new HashMap<>();
        rootComments.forEach(c -> parentMap.put(c.getId(), c));
        allReplies.forEach(c -> parentMap.put(c.getParentId(), rootComments.stream()
                .filter(r -> r.getId().equals(c.getParentId())).findFirst().orElse(null)));

        // 5. 组装回复树
        Map<String, List<CommentDTO>> replyMap = new HashMap<>();
        for (Comment reply : allReplies) {
            CommentDTO replyDTO = convertToCommentDTO(reply, currentUserId, userMap, parentMap);
            replyMap.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>()).add(replyDTO);
        }

        // 6. 构建结果
        return rootComments.stream().map(root -> {
            CommentDTO dto = convertToCommentDTO(root, currentUserId, userMap, parentMap);
            dto.setReplies(replyMap.getOrDefault(root.getId(), Collections.emptyList()));
            return dto;
        }).collect(Collectors.toList());
    }
    
    /**
     * 递归查找评论的顶级父评论（一级评论）的 ID
     */
    private String findRootParentId(Comment comment, Set<String> rootCommentIds, Map<String, Comment> commentMap) {
        String parentId = comment.getParentId();
        
        if (StringUtils.isBlank(parentId)) {
            log.info("  [findRoot] id={} 没有 parentId，返回 null", comment.getId());
            return null; // 没有父评论，不是回复
        }
        
        if (rootCommentIds.contains(parentId)) {
            log.info("  [findRoot] id={} 找到一级评论: {}", comment.getId(), parentId);
            return parentId; // 父评论是一级评论
        }
        
        if (commentMap.containsKey(parentId)) {
            Comment parentComment = commentMap.get(parentId);
            log.info("  [findRoot] id={} 递归查找 parentId={}", comment.getId(), parentId);
            return findRootParentId(parentComment, rootCommentIds, commentMap); // 递归查找
        }
        
        log.info("  [findRoot] id={} parentId={} 不在映射表中，返回 null", comment.getId(), parentId);
        return null; // 无法找到顶级父评论
    }

    @Override
    @Transactional
    public String addComment(CommentRequest request, String userId) {
        // 敏感词校验
        String matchedWord = sensitiveWordService.checkText(request.getContent());
        if (matchedWord != null) {
            throw new RuntimeException("评论失败，内容包含敏感词");
        }

        Comment comment = new Comment();
        comment.setPublishId(request.getPublishId());
        comment.setUserId(userId);
        comment.setParentId(StringUtils.isBlank(request.getParentId()) ? null : request.getParentId());
        comment.setContent(request.getContent());
        comment.setLikeCount(0);
        commentRepository.insert(comment);

        // 赚取积分：评论他人 +3 积分
        try {
            pointsService.earnPoints(userId, "comment", comment.getId(), "评论他人");
        } catch (Exception e) {
            log.warn("赚取积分失败: {}", e.getMessage());
        }

        // 更新发布表的评论数
        TripPublish publish = tripPublishRepository.selectById(request.getPublishId());
        if (publish != null) {
            publish.setCommentCount(publish.getCommentCount() == null ? 1 : publish.getCommentCount() + 1);
            tripPublishRepository.updateById(publish);

            // 发送消息给行程发布者
            if (!publish.getUserId().equals(userId)) {
                User sender = userRepository.selectById(userId);
                Message message = new Message();
                message.setUserId(publish.getUserId());
                message.setSenderId(userId);
                message.setType("comment");
                message.setContent((sender != null ? sender.getNickname() : "有人") + " 评论了你的行程「" + publish.getTitle() + "」：" + request.getContent());
                message.setTargetId(request.getPublishId());
                message.setTargetType("publish");
                messageService.createMessage(message);
            }
        }
        return comment.getId();
    }

    @Override
    @Transactional
    public void deleteComment(String commentId, String userId) {
        Comment comment = commentRepository.selectById(commentId);
        if (comment != null && comment.getUserId().equals(userId)) {
            commentRepository.deleteById(commentId);
            // 减少评论计数
            TripPublish publish = tripPublishRepository.selectById(comment.getPublishId());
            if (publish != null && publish.getCommentCount() != null && publish.getCommentCount() > 0) {
                publish.setCommentCount(publish.getCommentCount() - 1);
                tripPublishRepository.updateById(publish);
            }
        }
    }

    @Override
    @Transactional
    public String replyComment(CommentRequest request, String userId) {
        // 敏感词校验
        String matchedWord = sensitiveWordService.checkText(request.getContent());
        if (matchedWord != null) {
            throw new RuntimeException("回复失败，内容包含敏感词");
        }

        // 回复评论需要有 parentId
        if (request.getParentId() == null || request.getParentId().isEmpty()) {
            throw new IllegalArgumentException("回复评论需要指定父评论 ID");
        }

        log.info("回复评论, request: {}, userId: {}", request, userId);

        Comment comment = new Comment();
        comment.setPublishId(request.getPublishId());
        comment.setUserId(userId);
        comment.setParentId(request.getParentId());
        comment.setContent(request.getContent());
        comment.setLikeCount(0);
        commentRepository.insert(comment);

        log.info("回复评论成功, id: {}, parentId: {}", comment.getId(), comment.getParentId());

        // 赚取积分：回复评论 +3 积分
        try {
            pointsService.earnPoints(userId, "comment", comment.getId(), "回复评论");
        } catch (Exception e) {
            log.warn("赚取积分失败: {}", e.getMessage());
        }

        // 更新发布表的评论数
        TripPublish publish = tripPublishRepository.selectById(request.getPublishId());
        if (publish != null) {
            publish.setCommentCount(publish.getCommentCount() == null ? 1 : publish.getCommentCount() + 1);
            tripPublishRepository.updateById(publish);
        }

        // 发送消息给父评论的作者
        Comment parentComment = commentRepository.selectById(request.getParentId());
        if (parentComment != null && !parentComment.getUserId().equals(userId)) {
            User sender = userRepository.selectById(userId);
            Message message = new Message();
            message.setUserId(parentComment.getUserId());
            message.setSenderId(userId);
            message.setType("reply");
            message.setContent((sender != null ? sender.getNickname() : "有人") + " 回复了你的评论：" + request.getContent());
            message.setTargetId(request.getPublishId());
            message.setTargetType("publish");
            messageService.createMessage(message);
        }

        return comment.getId();
    }

    @Override
    @Transactional
    public void followUser(String followerId, String followingId) {
        if (followerId.equals(followingId)) return;

        long count = followRelationRepository.selectCount(
                new LambdaQueryWrapper<FollowRelation>()
                        .eq(FollowRelation::getFollowerId, followerId)
                        .eq(FollowRelation::getFollowingId, followingId));
        if (count == 0) {
            FollowRelation relation = new FollowRelation();
            relation.setFollowerId(followerId);
            relation.setFollowingId(followingId);
            followRelationRepository.insert(relation);

            // 赚取积分：关注他人 +1 积分
            try {
                pointsService.earnPoints(followerId, "follow", followingId, "关注他人");
            } catch (Exception e) {
                log.warn("赚取积分失败: {}", e.getMessage());
            }

            // 发送消息给被关注的用户
            User sender = userRepository.selectById(followerId);
            Message message = new Message();
            message.setUserId(followingId);
            message.setSenderId(followerId);
            message.setType("follow");
            message.setContent((sender != null ? sender.getNickname() : "有人") + " 关注了你");
            message.setTargetId(followerId);
            message.setTargetType("user");
            messageService.createMessage(message);
        }
    }

    @Override
    @Transactional
    public void unfollowUser(String followerId, String followingId) {
        followRelationRepository.delete(
                new LambdaQueryWrapper<FollowRelation>()
                        .eq(FollowRelation::getFollowerId, followerId)
                        .eq(FollowRelation::getFollowingId, followingId));
    }

    @Override
    public boolean isFollowed(String followerId, String followingId) {
        return followRelationRepository.selectCount(
                new LambdaQueryWrapper<FollowRelation>()
                        .eq(FollowRelation::getFollowerId, followerId)
                        .eq(FollowRelation::getFollowingId, followingId)) > 0;
    }

    @Override
    @Transactional
    public boolean toggleLike(String userId, String targetId, String targetType) {
        long exists = likeRecordRepository.exists(userId, targetId, targetType, "like");
        
        if (exists > 0) {
            // 取消点赞
            likeRecordRepository.delete(
                    new LambdaQueryWrapper<LikeRecord>()
                            .eq(LikeRecord::getUserId, userId)
                            .eq(LikeRecord::getTargetId, targetId)
                            .eq(LikeRecord::getTargetType, targetType)
                            .eq(LikeRecord::getActionType, "like"));
            
            // 根据类型更新对应的点赞数
            if ("comment".equals(targetType)) {
                Comment comment = commentRepository.selectById(targetId);
                if (comment != null) {
                    comment.setLikeCount(Math.max(0, (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) - 1));
                    commentRepository.updateById(comment);
                }
            } else {
                TripPublish publish = tripPublishRepository.selectById(targetId);
                if (publish != null) {
                    publish.setLikeCount(Math.max(0, (publish.getLikeCount() == null ? 0 : publish.getLikeCount()) - 1));
                    tripPublishRepository.updateById(publish);
                }
            }
            return false;
        } else {
            // 点赞
            LikeRecord record = new LikeRecord();
            record.setUserId(userId);
            record.setTargetId(targetId);
            record.setTargetType(targetType);
            record.setActionType("like");
            likeRecordRepository.insert(record);
            
            // 根据类型更新对应的点赞数
            if ("comment".equals(targetType)) {
                Comment comment = commentRepository.selectById(targetId);
                if (comment != null) {
                    comment.setLikeCount(comment.getLikeCount() == null ? 1 : comment.getLikeCount() + 1);
                    commentRepository.updateById(comment);

                    // 发送消息给评论作者
                    if (!comment.getUserId().equals(userId)) {
                        User sender = userRepository.selectById(userId);
                        Message message = new Message();
                        message.setUserId(comment.getUserId());
                        message.setSenderId(userId);
                        message.setType("like");
                        message.setContent((sender != null ? sender.getNickname() : "有人") + " 赞了你的评论");
                        message.setTargetId(targetId);
                        message.setTargetType("comment");
                        messageService.createMessage(message);
                        
                        // 赚取积分：被点赞 +2 积分
                        try {
                            pointsService.earnPoints(comment.getUserId(), "receive_like", targetId, "获得点赞");
                        } catch (Exception e) {
                            log.warn("赚取积分失败: {}", e.getMessage());
                        }
                    }
                }
            } else {
                TripPublish publish = tripPublishRepository.selectById(targetId);
                if (publish != null) {
                    publish.setLikeCount(publish.getLikeCount() == null ? 1 : publish.getLikeCount() + 1);
                    tripPublishRepository.updateById(publish);

                    // 发送消息给行程发布者
                    if (!publish.getUserId().equals(userId)) {
                        User sender = userRepository.selectById(userId);
                        Message message = new Message();
                        message.setUserId(publish.getUserId());
                        message.setSenderId(userId);
                        message.setType("like");
                        message.setContent((sender != null ? sender.getNickname() : "有人") + " 赞了你的行程「" + publish.getTitle() + "」");
                        message.setTargetId(targetId);
                        message.setTargetType("publish");
                        messageService.createMessage(message);
                        
                        // 赚取积分：被点赞 +2 积分
                        try {
                            pointsService.earnPoints(publish.getUserId(), "receive_like", targetId, "获得点赞");
                        } catch (Exception e) {
                            log.warn("赚取积分失败: {}", e.getMessage());
                        }
                    }
                }
            }
            return true;
        }
    }

    @Override
    @Transactional
    public boolean toggleFavorite(String userId, String targetId) {
        long exists = likeRecordRepository.exists(userId, targetId, "publish", "fav");

        TripPublish publish = tripPublishRepository.selectById(targetId);
        if (publish == null) {
            return false;
        }

        if (exists > 0) {
            likeRecordRepository.delete(
                    new LambdaQueryWrapper<LikeRecord>()
                            .eq(LikeRecord::getUserId, userId)
                            .eq(LikeRecord::getTargetId, targetId)
                            .eq(LikeRecord::getTargetType, "publish")
                            .eq(LikeRecord::getActionType, "fav"));

            publish.setFavCount(Math.max(0, (publish.getFavCount() == null ? 0 : publish.getFavCount()) - 1));
            tripPublishRepository.updateById(publish);
            return false;
        }

        LikeRecord record = new LikeRecord();
        record.setUserId(userId);
        record.setTargetId(targetId);
        record.setTargetType("publish");
        record.setActionType("fav");
        likeRecordRepository.insert(record);

        publish.setFavCount(publish.getFavCount() == null ? 1 : publish.getFavCount() + 1);
        tripPublishRepository.updateById(publish);
        return true;
    }

    @Override
    @Transactional
    public void cancelLike(String userId, String targetId, String targetType) {
        long exists = likeRecordRepository.exists(userId, targetId, targetType, "like");
        if (exists <= 0) {
            return;
        }

        likeRecordRepository.delete(
                new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getUserId, userId)
                        .eq(LikeRecord::getTargetId, targetId)
                        .eq(LikeRecord::getTargetType, targetType)
                        .eq(LikeRecord::getActionType, "like"));

        if ("comment".equals(targetType)) {
            Comment comment = commentRepository.selectById(targetId);
            if (comment != null) {
                comment.setLikeCount(Math.max(0, (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) - 1));
                commentRepository.updateById(comment);
            }
        } else {
            TripPublish publish = tripPublishRepository.selectById(targetId);
            if (publish != null) {
                publish.setLikeCount(Math.max(0, (publish.getLikeCount() == null ? 0 : publish.getLikeCount()) - 1));
                tripPublishRepository.updateById(publish);
            }
        }
    }

    @Override
    @Transactional
    public void cancelFavorite(String userId, String targetId) {
        long exists = likeRecordRepository.exists(userId, targetId, "publish", "fav");
        if (exists <= 0) {
            return;
        }

        likeRecordRepository.delete(
                new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getUserId, userId)
                        .eq(LikeRecord::getTargetId, targetId)
                        .eq(LikeRecord::getTargetType, "publish")
                        .eq(LikeRecord::getActionType, "fav"));

        TripPublish publish = tripPublishRepository.selectById(targetId);
        if (publish != null) {
            publish.setFavCount(Math.max(0, (publish.getFavCount() == null ? 0 : publish.getFavCount()) - 1));
            tripPublishRepository.updateById(publish);
        }
    }

    // ==================== 内部方法 ====================

    private PublishDTO convertToPublishDTO(TripPublish p, String currentUserId,
                                           Map<String, User> userMap,
                                           Set<String> followSet,
                                           Set<String> likeSet,
                                           Set<String> favSet) {
        PublishDTO dto = new PublishDTO();
        dto.setId(p.getId());
        dto.setUserId(p.getUserId());
        dto.setTripPlanId(p.getTripPlanId());
        dto.setTitle(p.getTitle());
        dto.setDescription(p.getDescription());
        dto.setContent(p.getContent());
        dto.setCoverImage(resolvePublishCoverImage(p.getCoverImage(), p.getTripPlanId()));
        dto.setLocation(p.getLocation());
        dto.setDays(p.getDays());
        dto.setNights(p.getNights());
        dto.setLikeCount(p.getLikeCount() == null ? 0 : p.getLikeCount());
        dto.setCommentCount(p.getCommentCount() == null ? 0 : p.getCommentCount());
        dto.setFavCount(p.getFavCount() == null ? 0 : p.getFavCount());
        dto.setPublishTime(formatTime(p.getCreatedAt()));

        // 解析标签
        if (StringUtils.isNotBlank(p.getTags())) {
            try {
                dto.setTags(JSON.parseArray(p.getTags(), String.class));
            } catch (Exception e) {
                dto.setTags(Collections.emptyList());
            }
        } else {
            dto.setTags(Collections.emptyList());
        }

        // 从预加载的 Map 获取用户信息，避免 N+1
        User user = userMap.get(p.getUserId());
        if (user != null) {
            dto.setNickname(user.getNickname());
            dto.setAvatar(user.getAvatar());
        } else {
            dto.setNickname("旅行家");
            dto.setAvatar(null);
        }

        // 判断当前用户交互状态（使用批量查询结果，无 N+1）
        if (StringUtils.isNotBlank(currentUserId)) {
            dto.setIsFollowed(followSet.contains(p.getUserId()));
            dto.setIsLiked(likeSet.contains(p.getId()));
            dto.setIsFaved(favSet.contains(p.getId()));
        } else {
            dto.setIsFollowed(false);
            dto.setIsLiked(false);
            dto.setIsFaved(false);
        }

        // 获取路线数据
        List<DayPlanDTO> dayList = buildDayList(p.getTripPlanId());
        dto.setDayList(dayList);

        // 计算费用汇总
        CostSummaryDTO costSummary = new CostSummaryDTO();
        if (dayList != null) {
            for (DayPlanDTO day : dayList) {
                if (day.getSpots() != null) {
                    for (SpotDTO spot : day.getSpots()) {
                        // 按景点类型归类费用
                        if (spot.getCost() != null) {
                            double c = spot.getCost().doubleValue();
                            String type = spot.getType() != null ? spot.getType() : "";
                            if (type.contains("交通") || type.contains("车")) {
                                costSummary.setTransport(costSummary.getTransport() + c);
                            } else if (type.contains("住宿") || type.contains("酒店") || type.contains("住")) {
                                costSummary.setHotel(costSummary.getHotel() + c);
                            } else if (type.contains("餐饮") || type.contains("美食") || type.contains("吃")) {
                                costSummary.setFood(costSummary.getFood() + c);
                            } else {
                                // 默认为门票
                                costSummary.setTicket(costSummary.getTicket() + c);
                            }
                        }
                    }
                }
            }
        }
        costSummary.setTotal(costSummary.getTransport() + costSummary.getHotel() + costSummary.getFood() + costSummary.getTicket());
        dto.setTotalCost(costSummary);
        dto.setImages(loadPublishImages(p.getId()));

        return dto;
    }

    /**
     * 构建每天的行程计划列表
     */
    private List<DayPlanDTO> buildDayList(String tripPlanId) {
        if (StringUtils.isBlank(tripPlanId)) {
            return Collections.emptyList();
        }

        try {
            LambdaQueryWrapper<TripDay> dayWrapper = new LambdaQueryWrapper<TripDay>()
                    .eq(TripDay::getTripId, tripPlanId)
                    .orderByAsc(TripDay::getDay);
            List<TripDay> tripDays = tripDayRepository.selectList(dayWrapper);

            if (tripDays == null || tripDays.isEmpty()) {
                return Collections.emptyList();
            }

            // 批量加载所有 spots，避免 N+1
            List<String> dayIds = tripDays.stream().map(TripDay::getId).collect(Collectors.toList());
            LambdaQueryWrapper<TripSpot> spotWrapper = new LambdaQueryWrapper<TripSpot>()
                    .in(TripSpot::getTripDayId, dayIds)
                    .orderByAsc(TripSpot::getOrderNum);
            List<TripSpot> allSpots = tripSpotRepository.selectList(spotWrapper);
            Map<String, List<TripSpot>> spotsByDay = allSpots.stream()
                    .collect(Collectors.groupingBy(TripSpot::getTripDayId));

            // 构建每天的行程
            List<DayPlanDTO> dayPlans = new ArrayList<>();
            for (TripDay tripDay : tripDays) {
                DayPlanDTO dayPlan = new DayPlanDTO();
                dayPlan.setDate(tripDay.getDate());

                List<TripSpot> daySpots = spotsByDay.getOrDefault(tripDay.getId(), Collections.emptyList());
                List<SpotDTO> spots = daySpots.stream().map(this::convertToSpotDTO).collect(Collectors.toList());
                dayPlan.setSpots(spots);

                dayPlans.add(dayPlan);
            }

            return dayPlans;
        } catch (Exception e) {
            log.error("构建路线数据失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 转换景点实体为 DTO
     */
    private SpotDTO convertToSpotDTO(TripSpot spot) {
        SpotDTO dto = new SpotDTO();
        dto.setId(spot.getId());
        dto.setName(spot.getName());
        dto.setAddress(spot.getAddress());
        dto.setDesc(spot.getTips()); // 使用 tips 作为 desc
        dto.setImage(spot.getImage());
        dto.setCoverImage(spot.getImage());
        dto.setArrivalTime(spot.getArrivalTime());
        dto.setType(spot.getCategory()); // 使用 category 作为 type
        dto.setTips(spot.getTips());
        dto.setTravelGuide(spot.getTravelGuide());
        // 解析 cost字符串为整数
        if (StringUtils.isNotBlank(spot.getCost())) {
            try {
                dto.setCost(Integer.parseInt(spot.getCost().replaceAll("[^0-9]", "")));
            } catch (NumberFormatException e) {
                dto.setCost(0);
            }
        } else {
            dto.setCost(0);
        }
        dto.setLatitude(spot.getLatitude());
        dto.setLongitude(spot.getLongitude());
        return dto;
    }

    private String resolvePublishCoverImage(String coverImage, String tripPlanId) {
        if (StringUtils.isNotBlank(coverImage)) {
            return coverImage;
        }
        if (StringUtils.isBlank(tripPlanId)) {
            return "";
        }

        TripPlan tripPlan = tripPlanRepository.selectById(tripPlanId);
        if (tripPlan != null && StringUtils.isNotBlank(tripPlan.getCoverImage())) {
            return tripPlan.getCoverImage();
        }

        List<TripDay> tripDays = tripDayRepository.findByTripId(tripPlanId);
        if (tripDays == null || tripDays.isEmpty()) {
            return "";
        }
        List<String> dayIds = tripDays.stream().map(TripDay::getId).collect(Collectors.toList());
        List<TripSpot> spots = tripSpotRepository.selectList(
                new LambdaQueryWrapper<TripSpot>()
                        .in(TripSpot::getTripDayId, dayIds)
                        .isNotNull(TripSpot::getImage)
                        .ne(TripSpot::getImage, "")
                        .orderByAsc(TripSpot::getOrderNum)
                        .last("LIMIT 1"));
        return spots.isEmpty() ? "" : spots.get(0).getImage();
    }

    private TripPublish findActivePublishByTripPlanId(String tripPlanId) {
        if (StringUtils.isBlank(tripPlanId)) {
            return null;
        }
        return tripPublishRepository.selectOne(new LambdaQueryWrapper<TripPublish>()
                .eq(TripPublish::getTripPlanId, tripPlanId)
                .eq(TripPublish::getStatus, 1)
                .last("LIMIT 1"));
    }

    private void validatePublishOwner(String tripPlanId, String userId) {
        if (StringUtils.isBlank(tripPlanId)) {
            throw new RuntimeException("请选择要发布的行程");
        }
        TripPlan tripPlan = tripPlanRepository.selectById(tripPlanId);
        if (tripPlan == null) {
            throw new RuntimeException("行程不存在");
        }
        if (!Objects.equals(tripPlan.getUserId(), userId)) {
            throw new RuntimeException("无权发布该行程");
        }
    }

    private List<String> buildDefaultTags(String preferences) {
        if (StringUtils.isBlank(preferences)) {
            return new ArrayList<>();
        }
        return Arrays.stream(preferences.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .limit(6)
                .collect(Collectors.toList());
    }

    private List<PublishImageDTO> buildDraftImages(String tripPlanId, String coverImage) {
        List<PublishImageDTO> images = new ArrayList<>();
        Set<String> imageUrls = new LinkedHashSet<>();
        int sortOrder = 0;

        if (StringUtils.isNotBlank(coverImage)) {
            PublishImageDTO cover = new PublishImageDTO();
            cover.setImageUrl(coverImage);
            cover.setImageType("cover");
            cover.setMediaType("image");
            cover.setSourceType("trip_cover");
            cover.setSortOrder(sortOrder++);
            images.add(cover);
            imageUrls.add(coverImage);
        }

        List<TripDay> tripDays = tripDayRepository.findByTripId(tripPlanId);
        if (tripDays == null || tripDays.isEmpty()) {
            return images;
        }

        List<String> dayIds = tripDays.stream().map(TripDay::getId).collect(Collectors.toList());
        List<TripSpot> spots = tripSpotRepository.selectList(new LambdaQueryWrapper<TripSpot>()
                .in(TripSpot::getTripDayId, dayIds)
                .isNotNull(TripSpot::getImage)
                .ne(TripSpot::getImage, "")
                .orderByAsc(TripSpot::getOrderNum));

        for (TripSpot spot : spots) {
            if (!imageUrls.add(spot.getImage())) {
                continue;
            }
            PublishImageDTO image = new PublishImageDTO();
            image.setImageUrl(spot.getImage());
            image.setImageType("gallery");
            image.setMediaType("image");
            image.setSourceType("spot_image");
            image.setSourceId(spot.getId());
            image.setSortOrder(sortOrder++);
            images.add(image);
        }

        return images;
    }

    private void replacePublishImages(String publishId, String tripPlanId, String userId,
                                      List<PublishImageDTO> images, String coverImage) {
        tripPublishImageRepository.delete(new LambdaQueryWrapper<TripPublishImage>()
                .eq(TripPublishImage::getPublishId, publishId));

        List<PublishImageDTO> saveImages = images == null ? new ArrayList<>() : images;
        if (saveImages.isEmpty() && StringUtils.isNotBlank(coverImage)) {
            PublishImageDTO cover = new PublishImageDTO();
            cover.setImageUrl(coverImage);
            cover.setImageType("cover");
            cover.setMediaType("image");
            cover.setSourceType("trip_cover");
            cover.setSortOrder(0);
            saveImages = Collections.singletonList(cover);
        }

        int index = 0;
        Set<String> imageUrls = new LinkedHashSet<>();
        for (PublishImageDTO imageDTO : saveImages) {
            if (imageDTO == null || StringUtils.isBlank(imageDTO.getImageUrl())) {
                continue;
            }
            if (!imageUrls.add(imageDTO.getImageUrl())) {
                continue;
            }
            TripPublishImage image = new TripPublishImage();
            image.setPublishId(publishId);
            image.setTripPlanId(tripPlanId);
            image.setUserId(userId);
            image.setImageUrl(imageDTO.getImageUrl());
            image.setImageType(StringUtils.isNotBlank(imageDTO.getImageType()) ? imageDTO.getImageType() : "gallery");
            image.setMediaType(StringUtils.isNotBlank(imageDTO.getMediaType()) ? imageDTO.getMediaType() : "image");
            image.setSourceType(StringUtils.isNotBlank(imageDTO.getSourceType()) ? imageDTO.getSourceType() : "upload");
            image.setSourceId(imageDTO.getSourceId());
            image.setSortOrder(imageDTO.getSortOrder() == null ? index : imageDTO.getSortOrder());
            image.setDeleted(0);
            tripPublishImageRepository.insert(image);
            index++;
        }
    }

    private List<PublishImageDTO> loadPublishImages(String publishId) {
        if (StringUtils.isBlank(publishId)) {
            return Collections.emptyList();
        }
        List<TripPublishImage> images = tripPublishImageRepository.selectList(new LambdaQueryWrapper<TripPublishImage>()
                .eq(TripPublishImage::getPublishId, publishId)
                .orderByAsc(TripPublishImage::getSortOrder)
                .orderByAsc(TripPublishImage::getCreatedAt));
        return images.stream().map(this::toPublishImageDTO).collect(Collectors.toList());
    }

    private PublishImageDTO toPublishImageDTO(TripPublishImage image) {
        PublishImageDTO dto = new PublishImageDTO();
        dto.setId(image.getId());
        dto.setImageUrl(image.getImageUrl());
        dto.setImageType(image.getImageType());
        dto.setMediaType(StringUtils.isNotBlank(image.getMediaType()) ? image.getMediaType() : "image");
        dto.setSourceType(image.getSourceType());
        dto.setSourceId(image.getSourceId());
        dto.setSortOrder(image.getSortOrder());
        return dto;
    }

    private CommentDTO convertToCommentDTO(Comment c, String currentUserId,
                                           Map<String, User> userMap,
                                           Map<String, Comment> parentMap) {
        CommentDTO dto = new CommentDTO();
        dto.setId(c.getId());
        dto.setPublishId(c.getPublishId());
        dto.setUserId(c.getUserId());
        dto.setParentId(c.getParentId());
        dto.setContent(c.getContent());
        dto.setLikeCount(c.getLikeCount() == null ? 0 : c.getLikeCount());
        dto.setCreateTime(formatTime(c.getCreatedAt()));

        // 从预加载 Map 获取用户信息，避免 N+1
        User user = userMap.get(c.getUserId());
        if (user != null) {
            dto.setNickname(user.getNickname());
            dto.setAvatar(user.getAvatar());
        } else {
            dto.setNickname("用户");
            dto.setAvatar(null);
        }

        // 如果是回复，从预加载 Map 获取被回复的用户昵称（避免 N+1）
        if (StringUtils.isNotBlank(c.getParentId())) {
            Comment parentComment = parentMap.get(c.getParentId());
            if (parentComment != null) {
                User parentUser = userMap.get(parentComment.getUserId());
                if (parentUser != null) {
                    dto.setReplyToNickname(parentUser.getNickname());
                }
            }
        }

        if (StringUtils.isNotBlank(currentUserId)) {
            dto.setIsLiked(likeRecordRepository.exists(currentUserId, c.getId(), "comment", "like") > 0);
        } else {
            dto.setIsLiked(false);
        }
        return dto;
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) return "刚刚";
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(time, now);
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = ChronoUnit.HOURS.between(time, now);
        if (hours < 24) return hours + "小时前";
        long days = ChronoUnit.DAYS.between(time, now);
        if (days < 7) return days + "天前";
        return time.toLocalDate().toString();
    }

    private String formatCount(int count) {
        if (count >= 10000) return String.format("%.1fw", count / 10000.0);
        if (count >= 1000) return String.format("%.1fk", count / 1000.0);
        return String.valueOf(count);
    }

    // ========== 批量加载工具方法 ==========

    /**
     * 从对象列表中提取 userId 并批量加载用户
     */
    private <T> Map<String, User> batchLoadUsers(List<T> items, java.util.function.Function<T, String> userIdExtractor) {
        Set<String> userIds = items.stream().map(userIdExtractor).filter(Objects::nonNull).collect(Collectors.toSet());
        return batchLoadUsersByIds(userIds);
    }

    /**
     * 根据 userId 集合批量加载用户
     */
    private Map<String, User> batchLoadUsersByIds(Set<String> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        List<User> users = userRepository.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    /**
     * 加载单个用户（用于单条记录场景）
     */
    private Map<String, User> loadSingleUser(String userId) {
        if (StringUtils.isBlank(userId)) return Collections.emptyMap();
        User user = userRepository.selectById(userId);
        if (user == null) return Collections.emptyMap();
        return Collections.singletonMap(user.getId(), user);
    }

    @Override
    public List<PublishDTO> getFavorites(String userId, String currentUserId, int page, int size) {
        return getInteractedPublishes(userId, currentUserId, page, size, "fav");
    }

    @Override
    public List<PublishDTO> getLikedPublishes(String userId, String currentUserId, int page, int size) {
        return getInteractedPublishes(userId, currentUserId, page, size, "like");
    }

    private List<PublishDTO> getInteractedPublishes(String userId, String currentUserId, int page, int size, String actionType) {
        List<String> publishIdsByAction = likeRecordRepository.findTargetIdsByUserId(
                userId, "publish", actionType);

        if (publishIdsByAction.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 分页截取
        int fromIndex = (page - 1) * size;
        if (fromIndex >= publishIdsByAction.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + size, publishIdsByAction.size());
        List<String> pageIds = publishIdsByAction.subList(fromIndex, toIndex);

        // 2. 批量查询发布数据
        LambdaQueryWrapper<TripPublish> wrapper = new LambdaQueryWrapper<TripPublish>()
                .in(TripPublish::getId, pageIds)
                .orderByDesc(TripPublish::getCreatedAt);
        List<TripPublish> list = tripPublishRepository.selectList(wrapper);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 按交互顺序排序（保持与 subList 一致）
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < pageIds.size(); i++) {
            orderMap.put(pageIds.get(i), i);
        }
        list.sort(Comparator.comparingInt(p -> orderMap.getOrDefault(p.getId(), 0)));

        // 4. 转为 DTO（批量查询交互状态，避免 N+1）
        Map<String, User> userMap = batchLoadUsers(list, TripPublish::getUserId);
        List<String> authorIds = list.stream().map(TripPublish::getUserId).distinct().collect(Collectors.toList());
        List<String> publishIds = list.stream().map(TripPublish::getId).collect(Collectors.toList());
        Set<String> followSet = followRelationRepository.findFollowingIdsIn(currentUserId, authorIds).stream().collect(Collectors.toSet());
        Set<String> likeSet = likeRecordRepository.findTargetIdsIn(currentUserId, "publish", "like", publishIds).stream().collect(Collectors.toSet());
        Set<String> favSet = likeRecordRepository.findTargetIdsIn(currentUserId, "publish", "fav", publishIds).stream().collect(Collectors.toSet());
        return list.stream().map(p -> convertToPublishDTO(p, currentUserId, userMap, followSet, likeSet, favSet)).collect(Collectors.toList());
    }

    @Override
    public List<UserDTO> getFollowingList(String userId, String currentUserId) {
        List<String> followingIds = followRelationRepository.findFollowingIds(userId);
        return buildFollowUserList(followingIds, currentUserId);
    }

    @Override
    public List<UserDTO> getFollowerList(String userId, String currentUserId) {
        List<String> followerIds = followRelationRepository.findFollowerIds(userId);
        return buildFollowUserList(followerIds, currentUserId);
    }

    private List<UserDTO> buildFollowUserList(List<String> userIds, String currentUserId) {
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<User> users = userRepository.selectBatchIds(userIds);
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < userIds.size(); i++) {
            orderMap.put(userIds.get(i), i);
        }
        users.sort(Comparator.comparingInt(user -> orderMap.getOrDefault(user.getId(), Integer.MAX_VALUE)));

        Set<String> currentFollowingSet = Collections.emptySet();
        if (StringUtils.isNotBlank(currentUserId)) {
            List<String> currentFollowingIds = followRelationRepository.findFollowingIds(currentUserId);
            currentFollowingSet = new HashSet<>(currentFollowingIds);
        }

        Set<String> finalCurrentFollowingSet = currentFollowingSet;
        return users.stream().map(user -> {
            UserDTO dto = new UserDTO();
            dto.setId(user.getId());
            dto.setNickname(user.getNickname());
            dto.setAvatar(user.getAvatar());
            dto.setSignature(user.getSignature());
            dto.setCity(user.getCity());
            dto.setFollowed(finalCurrentFollowingSet.contains(user.getId()));
            return dto;
        }).collect(Collectors.toList());
    }
}
