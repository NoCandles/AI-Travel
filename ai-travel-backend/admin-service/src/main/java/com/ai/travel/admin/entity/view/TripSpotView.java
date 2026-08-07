package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("trip_spots")
public class TripSpotView {
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
    private Boolean isReached;
}
