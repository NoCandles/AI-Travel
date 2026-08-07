package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 城市
 */
@Data
@TableName("city")
public class City implements Serializable {

    /** 行政区划代码 (主键) */
    @TableId
    private Integer id;

    /** 城市名称 */
    private String name;

    /** 全拼 */
    private String pinyin;

    /** 拼音首字母 (A-Z) */
    private String pinyinShort;

    /** 热门排序 (0-99, 越大越热门) */
    private Integer hot;

    /** 经度 */
    private BigDecimal lng;

    /** 纬度 */
    private BigDecimal lat;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
