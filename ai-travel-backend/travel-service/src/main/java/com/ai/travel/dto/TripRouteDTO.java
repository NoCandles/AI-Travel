package com.ai.travel.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 单路线 DTO — 用于多路线方案中的一条路线
 */
@Data
public class TripRouteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路线标识: seasonal / trendy / classic */
    private String id;

    /** 路线标题，如 "🌸 当月时令定制版" */
    private String title;

    /** 路线副标题/一句话简介 */
    private String subtitle;

    /** 每天行程（同 TripDayDTO 结构） */
    private List<TripDayDTO> days;
}
