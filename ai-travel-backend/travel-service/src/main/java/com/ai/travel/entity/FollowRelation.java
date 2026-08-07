package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 关注关系
 */
@Data
@TableName("follow_relation")
public class FollowRelation implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 关注者用户ID */
    private String followerId;

    /** 被关注用户ID */
    private String followingId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}