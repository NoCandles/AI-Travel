package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("trip_plans")
public class TripPlanView {
    private String id;
    private String name;
    private String destination;
    private String userId;
    private String status;
    private String travelMode;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
