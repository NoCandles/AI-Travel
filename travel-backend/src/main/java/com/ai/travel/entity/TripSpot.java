package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("trip_spots")
public class TripSpot implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String tripDayId;

    private String name;

    private String category;

    private String address;

    private Double latitude;

    private Double longitude;

    private Integer orderNum;

    private String arrivalTime;

    private String departureTime;

    private String duration;

    private String cost;

    private String tips;

    private String temperature;

    private String image;

    /** 是否已到达（行程进行中打卡用） */
    private Boolean isReached;

    /** 到达打卡时间 */
    private LocalDateTime reachedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}