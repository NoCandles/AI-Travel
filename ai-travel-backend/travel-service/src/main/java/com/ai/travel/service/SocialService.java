package com.ai.travel.service;

import com.ai.travel.dto.*;

import java.util.List;

public interface SocialService {

    /** 获取广场行程列表 */
    List<PublishDTO> getPublishedTrips(String category, String keyword, int page, int size, String currentUserId, String city, Integer days);

    /** 获取行程发布详情 */
    PublishDTO getPublishDetail(String publishId, String currentUserId);

    PublishDTO getPublishDraft(String tripPlanId, String userId);

    /** 发布行程 */
    String createPublish(PublishRequest request, String userId);

    /** 删除发布 */
    void deletePublish(String publishId, String userId);

    /** 获取热门标签 */
    List<HotTagDTO> getHotTags();

    /** 获取评论列表（分页） */
    List<CommentDTO> getComments(String publishId, String currentUserId, int page, int size);

    /** 添加评论 */
    String addComment(CommentRequest request, String userId);

    /** 删除评论 */
    void deleteComment(String commentId, String userId);

    /** 回复评论 */
    String replyComment(CommentRequest request, String userId);

    /** 关注用户 */
    void followUser(String followerId, String followingId);

    /** 取消关注 */
    void unfollowUser(String followerId, String followingId);

    /** 检查是否已关注 */
    boolean isFollowed(String followerId, String followingId);

    /** 点赞/取消点赞 */
    boolean toggleLike(String userId, String targetId, String targetType);

    /** 取消点赞 */
    void cancelLike(String userId, String targetId, String targetType);

    /** 收藏/取消收藏 */
    boolean toggleFavorite(String userId, String targetId);

    /** 取消收藏 */
    void cancelFavorite(String userId, String targetId);

    /** 获取用户收藏的行程列表（分页） */
    List<PublishDTO> getFavorites(String userId, String currentUserId, int page, int size);

    /** 获取用户点赞过的行程列表（分页） */
    List<PublishDTO> getLikedPublishes(String userId, String currentUserId, int page, int size);

    /** 获取关注列表 */
    List<UserDTO> getFollowingList(String userId, String currentUserId);

    /** 获取粉丝列表 */
    List<UserDTO> getFollowerList(String userId, String currentUserId);
}
