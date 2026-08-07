package com.ai.travel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class CreateTripRequest implements Serializable {

    @NotBlank(message = "目的地不能为空")
    private String destination;

    @NotBlank(message = "开始日期不能为空")
    private String startDate;

    @NotBlank(message = "结束日期不能为空")
    private String endDate;

    @NotNull(message = "行程天数不能为空")
    private Integer days;

    private List<String> preferences;
    private List<String> mustVisitPlaces;
    private String budget;
    private String startPoint;
    private String endPoint;
    private String travelMode;

    /** 徒步专属参数（travelMode=hiking 时使用） */
    private HikingProfileDTO hikingProfile;
}
