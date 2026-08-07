package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("im_conversation")
public class ImConversation implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userAId;

    private String userBId;

    private String lastMessage;

    private String lastMessageType;

    private String lastRouteId;

    private Integer unreadA;

    private Integer unreadB;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
