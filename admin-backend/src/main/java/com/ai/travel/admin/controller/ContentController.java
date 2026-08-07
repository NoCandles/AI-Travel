package com.ai.travel.admin.controller;

import com.ai.travel.admin.dto.ApiResponse;
import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.entity.view.TripPublishView;
import com.ai.travel.admin.entity.view.CommentView;
import com.ai.travel.admin.config.AdminLog;
import com.ai.travel.admin.service.AdminContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/content")
@RequiredArgsConstructor
public class ContentController {

    private final AdminContentService contentService;

    // ===================== 发布审核 =====================

    /**
     * 发布列表
     */
    @GetMapping("/publish")
    public ApiResponse<PageResult<TripPublishView>> listPublish(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer reviewStatus,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(contentService.listPublish(page, size, reviewStatus, keyword));
    }

    /**
     * 下架/恢复发布
     */
    @AdminLog(module = "content", action = "update", targetType = "publish")
    @PutMapping("/publish/{id}/status")
    public ApiResponse<Void> updatePublishStatus(
            @PathVariable String id,
            @RequestParam Integer reviewStatus,
            @RequestParam(required = false) String reason) {
        contentService.updatePublishStatus(id, reviewStatus, reason);
        String msg = reviewStatus == 1 ? "恢复成功" : "下架成功";
        return ApiResponse.success(null, msg);
    }

    /**
     * 删除发布
     */
    @AdminLog(module = "content", action = "delete", targetType = "publish")
    @DeleteMapping("/publish/{id}")
    public ApiResponse<Void> deletePublish(@PathVariable String id) {
        contentService.deletePublish(id);
        return ApiResponse.success(null, "删除成功");
    }

    // ===================== 评论管理 =====================

    /**
     * 评论列表
     */
    @GetMapping("/comments")
    public ApiResponse<PageResult<CommentView>> listComments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userId) {
        return ApiResponse.success(contentService.listComments(page, size, keyword, userId));
    }

    /**
     * 删除评论
     */
    @AdminLog(module = "content", action = "delete", targetType = "comment")
    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        contentService.deleteComment(id);
        return ApiResponse.success(null, "删除成功");
    }
}
