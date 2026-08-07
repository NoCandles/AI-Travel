package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("trip_days")
public class TripDay implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String tripId;

    private Integer day;

    private String date;

    private String weather;

    private String temperature;

    private String notes;

    private String hotelName;

    private String hotelDetail;

    private Integer hotelPrice;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
