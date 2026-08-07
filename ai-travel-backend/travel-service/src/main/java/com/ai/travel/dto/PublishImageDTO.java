package com.ai.travel.dto;

import lombok.Data;

@Data
public class PublishImageDTO {
    private String id;
    private String imageUrl;
    private String imageType;
    private String mediaType;
    private String sourceType;
    private String sourceId;
    private Integer sortOrder;
}
