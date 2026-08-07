package com.ai.travel.dto;

import lombok.Data;
import java.util.List;

/**
 * 每天的行程计划 DTO
 */
@Data
public class DayPlanDTO {
    private String date; // 日期，如 "2024-01-15"
    private List<SpotDTO> spots;  // 当天的景点列表
}