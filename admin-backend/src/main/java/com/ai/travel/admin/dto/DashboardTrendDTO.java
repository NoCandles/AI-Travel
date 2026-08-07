package com.ai.travel.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTrendDTO {

    /** 日期 (yyyy-MM-dd) */
    private String date;
    /** 当日新增用户 */
    private long newUsers;
    /** 当日新增行程 */
    private long newTrips;
    /** 当日发布数 */
    private long newPublish;
    /** 当日评论数 */
    private long newComments;
}
