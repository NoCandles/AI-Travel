package com.ai.travel.dto.packing;

import lombok.Data;
import java.util.List;

@Data
public class PackingListResponse {
    private List<PackingCategoryDTO> categories;
}
