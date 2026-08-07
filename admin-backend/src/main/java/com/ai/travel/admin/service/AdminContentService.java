package com.ai.travel.admin.service;

import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.entity.view.TripPublishView;
import com.ai.travel.admin.entity.view.CommentView;
import com.ai.travel.admin.repository.view.TripPublishViewRepository;
import com.ai.travel.admin.repository.view.CommentViewRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminContentService {

    private final TripPublishViewRepository publishRepository;
    private final CommentViewRepository commentRepository;

    // ===================== 发布审核 =====================

    /**
     * 发布列表（支持状态/关键词筛选）
     */
    public PageResult<TripPublishView> listPublish(int page, int size, Integer reviewStatus, String keyword) {
        LambdaQueryWrapper<TripPublishView> w = new LambdaQueryWrapper<>();
        if (reviewStatus != null) {
            w.eq(TripPublishView::getReviewStatus, reviewStatus);
        }
        if (keyword != null && !keyword.isEmpty()) {
            w.like(TripPublishView::getTitle, keyword);
        }
        w.orderByDesc(TripPublishView::getCreatedAt);

        Page<TripPublishView> p = publishRepository.selectPage(new Page<>(page, size), w);
        return PageResult.of(p);
    }

    /**
     * 下架/恢复发布
     */
    public void updatePublishStatus(String id, Integer reviewStatus, String reason) {
        TripPublishView pub = publishRepository.selectById(id);
        if (pub == null) {
            throw new RuntimeException("发布不存在");
        }
        pub.setReviewStatus(reviewStatus);
        pub.setReviewReason(reason);
        publishRepository.updateById(pub);
    }

    /**
     * 删除发布（物理删除 + 级联删除评论）
     */
    public void deletePublish(String id) {
        TripPublishView pub = publishRepository.selectById(id);
        if (pub == null) {
            throw new RuntimeException("发布不存在");
        }
        // 级联删除评论
        commentRepository.delete(
                new LambdaQueryWrapper<CommentView>().eq(CommentView::getPublishId, id));
        publishRepository.deleteById(id);
    }

    // ===================== 评论管理 =====================

    /**
     * 评论列表
     */
    public PageResult<CommentView> listComments(int page, int size, String keyword, String userId) {
        LambdaQueryWrapper<CommentView> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            w.like(CommentView::getContent, keyword);
        }
        if (userId != null && !userId.isEmpty()) {
            w.eq(CommentView::getUserId, userId);
        }
        w.orderByDesc(CommentView::getCreatedAt);

        Page<CommentView> p = commentRepository.selectPage(new Page<>(page, size), w);
        return PageResult.of(p);
    }

    /**
     * 删除评论
     */
    public void deleteComment(Long id) {
        commentRepository.deleteById(id);
    }
}
