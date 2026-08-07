package com.ai.travel.dto;

import lombok.Data;

/**
 * 景点 DTO
 */
@Data
public class SpotDTO {
    private String id;
    private String name;
    private String address;
    private String desc;
    private String image;
    private String coverImage;
    private String arrivalTime;
    private String type;
    private String tips;
    private Integer cost;
    private Double latitude;
    private Double longitude;
}