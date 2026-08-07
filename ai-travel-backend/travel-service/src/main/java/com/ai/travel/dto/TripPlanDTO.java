package com.ai.travel.dto;

import com.ai.travel.dto.packing.PackingCategoryDTO;
import com.ai.travel.dto.TripRouteDTO;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class TripPlanDTO implements Serializable {

    private String id;
    private String name;
    private String destination;
    private String startDate;
    private String endDate;
    private String description;
    private String status;
    private String userId;
    private List<String> preferences;
    private List<String> mustVisitPlaces;
    private String budget;
    private String startPoint;
    private String endPoint;
    private String travelMode;
    private Integer totalDistance;
    private List<TripDayDTO> days;
    private List<TripRouteDTO> routes;

    /** ✅ 行李清单 */
    private List<PackingCategoryDTO> packingList;

    /** 封面图片URL */
    private String coverImage;
}
