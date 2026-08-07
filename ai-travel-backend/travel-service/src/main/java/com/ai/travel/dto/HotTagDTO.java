package com.ai.travel.dto;

import lombok.Data;

/**
 * 热门标签 DTO
 */
@Data
public class HotTagDTO {
    private String name;
    private String image;
    private String count;
}