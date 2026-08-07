package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("hiking_segment")
public class HikingSegment implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 关联 hiking_route.id */
    private String hikingRouteId;

    /** 第几天 */
    private Integer dayNum;

    /** 段内排序 */
    private Integer orderNum;

    /** 路段名称: 起点→点位1 */
    private String name;

    /** 距离(km) */
    private BigDecimal distance;

    /** 爬升(m) */
    private Integer ascent;

    /** 下降(m) */
    private Integer descent;

    /** 路况: 土路/台阶/栈道/野路 */
    private String roadType;

    /** 坡度: 缓坡/中坡/陡坡 */
    private String slope;

    /** 亮点景观 */
    private String highlights;

    /** 休息点位置 */
    private String restPoint;

    /** 风险提示 */
    private String riskTip;

    /** 信号: 良好/弱/无信号 */
    private String signalStrength;

    /** 起点名称 */
    private String startPointName;

    /** 终点名称 */
    private String endPointName;

    /** 起点纬度 */
    private BigDecimal startLat;

    /** 起点经度 */
    private BigDecimal startLng;

    /** 终点纬度 */
    private BigDecimal endLat;

    /** 终点经度 */
    private BigDecimal endLng;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
