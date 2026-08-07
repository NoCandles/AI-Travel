package com.ai.travel.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 徒步路线专属参数
 */
@Data
public class HikingProfileDTO implements Serializable {

    /** 出行人数类型：solo/parent-child/team */
    private String groupType;

    /** 期望里程：如 1-3km, 3-8km, 8-15km, 15km以上 */
    private String distanceRange;

    /** 可用时长：half-day/full-day/multi-day */
    private String durationType;

    /** 路线类型（多选）：环线/单程/往返/登山穿越/溯溪/古道 */
    private List<String> routeTypes;

    /** 难度偏好 1-5 */
    private Integer difficulty;

    /** 景观偏好（多选）：山林/溪流瀑布/云海观景/古村落/花海草原/雪景/人文古迹 */
    private List<String> sceneryPrefs;

    /** 路况要求 */
    private String roadCondition;

    /** 体力限制（多选）：老人同行/小孩同行/体能一般 */
    private List<String> physicalLimit;

    /** 交通限制 */
    private String trafficLimit;

    /** 硬性规避（多选）：避开收费景区/避开人流密集区/避开野路危险路段 */
    private List<String> avoidItems;

    /** 是否需要露营地 */
    private Boolean needCampsite;

    /** 附加需求（多选） */
    private List<String> advancedNeeds;
}
