package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("trip_publish")
public class TripPublishView {
    private String id;
    private String userId;
    private String tripPlanId;
    private String title;
    private String content;
    private String coverImage;
    private Integer status;
    private Integer reviewStatus;
    private String reviewReason;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favCount;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
