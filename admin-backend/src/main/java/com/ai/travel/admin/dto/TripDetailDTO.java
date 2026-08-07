package com.ai.travel.admin.dto;

import com.ai.travel.admin.entity.view.TripDayView;
import com.ai.travel.admin.entity.view.TripSpotView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDetailDTO {

    private String id;
    private String name;
    private String destination;
    private String startDate;
    private String endDate;
    private String description;
    private String status;
    private String userId;
    private String travelMode;
    private String createdAt;

    /** 天数列表 */
    private List<TripDayView> days;
    /** 每个天的景点 */
    private List<List<TripSpotView>> spotsByDay;
}
