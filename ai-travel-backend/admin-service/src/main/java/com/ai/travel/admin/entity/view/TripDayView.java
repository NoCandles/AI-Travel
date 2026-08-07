package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("trip_days")
public class TripDayView {
    private String id;
    private String tripId;
    private Integer number;
    private String date;
    private String weather;
    private String temperature;
    private String notes;
    private String hotelName;
    private String hotelDetail;
    private String hotelPrice;
}
