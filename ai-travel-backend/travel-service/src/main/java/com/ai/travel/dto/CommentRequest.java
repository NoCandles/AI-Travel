package com.ai.travel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 评论请求
 */
@Data
public class CommentRequest {

    @NotBlank(message = "发布 ID 不能为空")
    private String publishId;

    @NotBlank(message = "评论内容不能为空")
    private String content;

    /** 父评论 ID（回复时使用） */
    private String parentId;
}
