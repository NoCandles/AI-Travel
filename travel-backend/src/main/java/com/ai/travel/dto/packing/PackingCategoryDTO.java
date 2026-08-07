package com.ai.travel.dto.packing;

import lombok.Data;
import java.util.List;

@Data
public class PackingCategoryDTO {
    private String name;
    private List<PackingItemDTO> items;
}
