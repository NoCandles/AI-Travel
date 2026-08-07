package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("trip_plans")
public class TripPlan implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;

    private String destination;

    private String startDate;

    private String endDate;

    private String description;

    private String status;

    private String userId;

    private String preferences;

    private String mustVisitPlaces;

    private String budget;

    private Integer totalDistance;

    private String startPoint;

    private String endPoint;

    private String travelMode;

    private String routesJson;

    private String versionsJson;

    private String packingListJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
