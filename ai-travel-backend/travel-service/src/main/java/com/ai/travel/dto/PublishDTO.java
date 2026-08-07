package com.ai.travel.dto;

import lombok.Data;

import java.util.List;

@Data
public class PublishDTO {
    private String id;
    private String userId;
    private String tripPlanId;
    private String nickname;
    private String avatar;
    private String title;
    private String description;
    private String content;
    private String coverImage;
    private String location;
    private Integer days;
    private Integer nights;
    private List<String> tags;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favCount;
    private String publishTime;
    private Boolean isFollowed;
    private Boolean isLiked;
    private Boolean isFaved;
    private List<DayPlanDTO> dayList;
    private CostSummaryDTO totalCost;
    private List<PublishImageDTO> images;
}
