package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("comment")
public class CommentView {
    private String id;
    private String publishId;
    private String userId;
    private String parentId;
    private String content;
    private Integer likeCount;
    private Integer reviewStatus;
    private String reviewReason;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
