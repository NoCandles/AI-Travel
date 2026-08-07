package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String senderId;

    private String type;

    private String content;

    private String targetId;

    private String targetType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer isRead;

    @TableField(exist = false)
    private String senderName;

    @TableField(exist = false)
    private String senderAvatar;

    public Boolean getIsRead() {
        return isRead != null && isRead == 1;
    }

    public void setIsRead(Boolean read) {
        this.isRead = read ? 1 : 0;
    }
}
