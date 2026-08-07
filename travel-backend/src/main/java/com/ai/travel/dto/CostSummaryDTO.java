package com.ai.travel.dto;

import lombok.Data;

/**
 * 费用汇总 DTO
 */
@Data
public class CostSummaryDTO {
    private double transport = 0;
    private double hotel = 0;
    private double food = 0;
    private double ticket = 0;
    private double total = 0;
}
