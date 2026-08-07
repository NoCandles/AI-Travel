package com.ai.travel.dto;

import lombok.Data;

/**
 * 发布行程请求
 */
@Data
public class PublishRequest {
    private String tripPlanId;
    private String title;
    private String description;
    private String coverImage;
    private String location;
    private Integer days;
    private Integer nights;
    private String tags; // JSON数组字符串
}