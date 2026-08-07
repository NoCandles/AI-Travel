package com.ai.travel.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("admin_logs")
public class AdminLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String adminId;

    private String adminName;

    private String module;

    private String action;

    private String targetType;

    private String targetId;

    private String detail;

    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
