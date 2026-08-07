package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("trip_publish_images")
public class TripPublishImage implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String publishId;

    private String tripPlanId;

    private String userId;

    private String imageUrl;

    private String imageType;

    private String mediaType;

    private String sourceType;

    private String sourceId;

    private Integer sortOrder;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
