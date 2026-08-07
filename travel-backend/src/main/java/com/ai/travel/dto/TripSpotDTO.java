package com.ai.travel.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class TripSpotDTO implements Serializable {

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

    /** 是否已到达 */
    private Boolean isReached;

    /** 到达打卡时间 */
    private String reachedAt;
}
