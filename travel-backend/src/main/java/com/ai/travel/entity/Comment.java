package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论
 */
@Data
@TableName("comment")
public class Comment implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 关联的发布ID */
    private String publishId;

    /** 评论者用户ID */
    private String userId;

    /** 父评论ID（用于回复） */
    private String parentId;

    /** 评论内容 */
    private String content;

    /** 点赞数 */
    private Integer likeCount;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}