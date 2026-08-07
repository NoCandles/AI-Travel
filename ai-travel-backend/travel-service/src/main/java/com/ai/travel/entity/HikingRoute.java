package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("hiking_route")
public class HikingRoute implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 关联 trip_plans.id */
    private String tripPlanId;

    /** 路线名称 */
    private String routeName;

    /** 总里程(km) */
    private BigDecimal totalDistance;

    /** 累计爬升(m) */
    private Integer totalAscent;

    /** 累计下降(m) */
    private Integer totalDescent;

    /** 纯步行时长 */
    private String walkTime;

    /** 含休息总时长 */
    private String totalTime;

    /** 难度 1-5 */
    private Integer difficulty;

    /** 路线类型: 环线/单程/往返/登山穿越/溯溪/古道 */
    private String routeType;

    /** 路况占比 JSON */
    private String roadRatio;

    /** 是否有露营地 */
    private Integer hasCampsite;

    /** 是否亲子友好 */
    private Integer isFamilyFriendly;

    /** 封面图URL */
    private String coverImage;

    /** 安全指南JSON */
    private String safetyJson;

    /** 装备建议JSON */
    private String gearJson;

    /** 补给信息JSON */
    private String supplyJson;

    /** 是否公开到广场 */
    private Integer isPublic;

    /** 浏览次数 */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 创建者用户ID */
    private String creatorId;

    /** 来源: ai/manual */
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
