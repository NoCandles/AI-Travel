package com.ai.travel.controller;

import com.ai.travel.dto.*;
import com.ai.travel.service.SocialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 社交功能控制器（广场/评论/关注/点赞）
 */
@Slf4j
@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    // ==================== 行程广场 ====================

    /**
     * 获取广场行程列表
     */
    @GetMapping("/publish")
    public ApiResponse<List<PublishDTO>> getPublishedTrips(
            @RequestParam(defaultValue = "recommend") String category,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer days) {
        List<PublishDTO> list = socialService.getPublishedTrips(category, keyword, page, size, userId, city, days);
        return ApiResponse.success(list);
    }

    /**
     * 获取行程发布详情
     */
    @GetMapping("/publish/detail")
    public ApiResponse<PublishDTO> getPublishDetail(
            @RequestParam String publishId,
            @RequestParam(required = false) String userId) {
        PublishDTO dto = socialService.getPublishDetail(publishId, userId);
        if (dto == null) {
            return ApiResponse.error("行程发布不存在");
        }
        return ApiResponse.success(dto);
    }

    /**
     * 发布行程
     */
    @PostMapping("/publish")
    public ApiResponse<String> createPublish(@RequestBody PublishRequest request,
                                              @RequestHeader("X-User-Id") String userId) {
        String id = socialService.createPublish(request, userId);
        return ApiResponse.success(id);
    }

    /**
     * 删除发布
     */
    @DeleteMapping("/publish/{publishId}")
    public ApiResponse<Void> deletePublish(@PathVariable String publishId,
                                            @RequestHeader("X-User-Id") String userId) {
        socialService.deletePublish(publishId, userId);
        return ApiResponse.success(null);
    }

    /**
     * 获取热门标签
     */
    @GetMapping("/publish/hot-tags")
    public ApiResponse<List<HotTagDTO>> getHotTags() {
        return ApiResponse.success(socialService.getHotTags());
    }

    /**
     * 获取用户的发布列表
     */
    @GetMapping("/publish/user/{userId}")
    public ApiResponse<List<PublishDTO>> getUserPublish(
            @PathVariable String userId,
            @RequestParam(required = false) String currentUserId) {
        List<PublishDTO> list = socialService.getPublishedTrips("", "", 1, 100, currentUserId, null, null);
        // 过滤仅该用户的发布
        list.removeIf(p -> !p.getUserId().equals(userId));
        return ApiResponse.success(list);
    }

    // ==================== 评论 ====================

    /**
     * 获取评论列表（分页）
     */
    @GetMapping("/comment/list")
    public ApiResponse<List<CommentDTO>> getComments(
            @RequestParam String publishId,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(socialService.getComments(publishId, userId, page, size));
    }

    /**
     * 发表评论
     */
    @PostMapping("/comment")
    public ApiResponse<String> addComment(@RequestBody @Valid CommentRequest request,
                                           @RequestHeader("X-User-Id") String userId) {
        String id = socialService.addComment(request, userId);
        return ApiResponse.success(id);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/comment/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable String commentId,
                                            @RequestHeader("X-User-Id") String userId) {
        socialService.deleteComment(commentId, userId);
        return ApiResponse.success(null);
    }

    /**
     * 回复评论
     */
    @PostMapping("/comment/reply")
    public ApiResponse<String> replyComment(@RequestBody @Valid CommentRequest request,
                                             @RequestHeader("X-User-Id") String userId) {
        String id = socialService.replyComment(request, userId);
        return ApiResponse.success(id);
    }

    // ==================== 关注 ====================

    /**
     * 关注用户
     */
    @PostMapping("/follow")
    public ApiResponse<Void> followUser(@RequestParam String followingId,
                                         @RequestHeader("X-User-Id") String userId) {
        socialService.followUser(userId, followingId);
        return ApiResponse.success(null);
    }

    /**
     * 取消关注
     */
    @DeleteMapping("/follow")
    public ApiResponse<Void> unfollowUser(@RequestParam String followingId,
                                           @RequestHeader("X-User-Id") String userId) {
        socialService.unfollowUser(userId, followingId);
        return ApiResponse.success(null);
    }

    /**
     * 检查是否已关注
     */
    @GetMapping("/follow/check")
    public ApiResponse<Boolean> checkFollow(@RequestParam String followingId,
                                             @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(socialService.isFollowed(userId, followingId));
    }

    // ==================== 点赞 ====================

    /**
     * 点赞/取消点赞
     */
    @PostMapping("/like")
    public ApiResponse<Boolean> toggleLike(@RequestParam String targetId,
                                            @RequestParam(defaultValue = "publish") String targetType,
                                            @RequestHeader("X-User-Id") String userId) {
        boolean liked = socialService.toggleLike(userId, targetId, targetType);
        return ApiResponse.success(liked);
    }

    /**
     * 获取用户收藏的行程列表（分页）
     */
    @GetMapping("/publish/favorites")
    public ApiResponse<List<PublishDTO>> getFavorites(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<PublishDTO> list = socialService.getFavorites(userId, userId, page, size);
        return ApiResponse.success(list);
    }
}