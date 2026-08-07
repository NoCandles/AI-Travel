package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 行程发布（广场文章）
 */
@Data
@TableName("trip_publish")
public class TripPublish implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 发布者用户ID */
    private String userId;

    /** 关联的行程计划ID（可选） */
    private String tripPlanId;

    /** 标题 */
    private String title;

    /** 描述 */
    private String description;

    private String content;

    /** 封面图URL */
    private String coverImage;

    /** 目的地/地点 */
    private String location;

    /** 天数 */
    private Integer days;

    /** 晚数 */
    private Integer nights;

    /** 标签（JSON数组字符串） */
    private String tags;

    /** 状态：0-草稿 1-已发布 */
    private Integer status;

    /** 浏览数 */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 收藏数 */
    private Integer favCount;

    private Integer reviewStatus;

    private String reviewReason;

    /** 是否删除：0-正常 1-删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
