package com.ai.travel.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class TripHotelDTO implements Serializable {

    private String name;
    private String detail;
    private Integer price;
}
