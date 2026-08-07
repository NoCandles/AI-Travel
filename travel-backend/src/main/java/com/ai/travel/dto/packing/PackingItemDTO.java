package com.ai.travel.dto.packing;

import lombok.Data;

@Data
public class PackingItemDTO {
    private String name;
    private Integer quantity;
    private Boolean checked = false;
    private String note;
}
