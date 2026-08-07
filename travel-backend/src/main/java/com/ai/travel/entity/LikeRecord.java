package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 点赞/收藏记录
 */
@Data
@TableName("like_record")
public class LikeRecord implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 操作用户ID */
    private String userId;

    /** 目标ID（发布ID或评论ID） */
    private String targetId;

    /** 目标类型：publish-行程发布 comment-评论 */
    private String targetType;

    /** 操作类型：like-点赞 fav-收藏 */
    private String actionType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}