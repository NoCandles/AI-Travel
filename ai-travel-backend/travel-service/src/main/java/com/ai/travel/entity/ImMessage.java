package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("im_message")
public class ImMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private String senderId;

    private String receiverId;

    private String type;

    private String content;

    private String routeId;

    private String routeTitle;

    private String routeCover;

    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
