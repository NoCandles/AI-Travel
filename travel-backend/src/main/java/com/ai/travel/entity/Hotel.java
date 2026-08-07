package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("hotels")
public class Hotel implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String hotelId;

    private String name;

    private String address;

    private String tel;

    private Double latitude;

    private Double longitude;

    private Integer distance;

    private String city;

    private String category;

    private String tags;

    private String source;

    private Double searchLat;

    private Double searchLng;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
