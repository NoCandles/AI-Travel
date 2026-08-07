package com.ai.travel.dto;

import lombok.Data;

import java.util.List;

@Data
public class PublishRequest {
    private String tripPlanId;
    private String title;
    private String description;
    private String content;
    private String coverImage;
    private String location;
    private Integer days;
    private Integer nights;
    private String tags;
    private List<PublishImageDTO> images;
}
