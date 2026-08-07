package com.ai.travel.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDTO {

    /** 总用户数 */
    private long totalUsers;
    /** 今日新增用户 */
    private long todayNewUsers;
    /** 总行程数 */
    private long totalTrips;
    /** 今日生成行程 */
    private long todayTrips;
    /** 已发布到广场 */
    private long totalPublished;
    /** 总评论数 */
    private long totalComments;
    /** 今日活跃用户 */
    private long todayActiveUsers;
}
