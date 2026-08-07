package com.ai.travel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 4 分片 AI 调用结果封装
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShardResult {

    /**
     * 分片 1：当月时令定制版行程（完整 N 天 JSON）
     */
    private String shard1;

    /**
     * 分片 2：当下网红爆款版行程（完整 N 天 JSON）
     */
    private String shard2;

    /**
     * 分片 3：经典稳妥路线行程（完整 N 天 JSON）
     */
    private String shard3;

    /**
     * 分片 4：食宿推荐（3 套风格，按天匹配）
     * { seasonal: {days}, trendy: {days}, classic: {days} }
     */
    private String shard4;
}
