package com.ai.travel.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class TripDayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String tripId;
    private Integer day;
    private String date;
    private String weather;
    private String temperature;
    private String notes;
    private List<TripSpotDTO> spots;
    private TripHotelDTO hotel;
}
