package com.ai.travel.common.enums;

/**
 * 行程状态枚举
 * 替代全项目中的裸字符串 "GENERATING"、"SAVED" 等
 */
public enum TripStatus {
    DRAFT("草稿"),
    GENERATING("生成中"),
    SAVED("已保存"),
    ONGOING("进行中"),
    COMPLETED("已完成"),
    FAILED("生成失败");

    private final String label;

    TripStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 从字符串转换（兼容旧代码中的裸字符串）
     */
    public static TripStatus from(String value) {
        if (value == null) return null;
        for (TripStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("未知的行程状态: " + value);
    }
}
