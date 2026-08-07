package com.ai.travel.dto;

import lombok.Data;
import java.util.List;

/**
 * 广场行程发布 DTO（前端所需完整数据）
 */
@Data
public class PublishDTO {
    private String id;
    private String userId;
    private String tripPlanId;
    private String nickname;
    private String avatar;
    private String title;
    private String description;
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
    
    // 路线数据（包含每天的景点）
    private List<DayPlanDTO> dayList;

    // 费用汇总
    private CostSummaryDTO totalCost;
}