package com.ai.travel.admin.controller;

import com.ai.travel.admin.config.AdminLog;
import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.dto.TripDetailDTO;
import com.ai.travel.admin.entity.view.TripPlanView;
import com.ai.travel.admin.service.AdminTripService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/trips")
@RequiredArgsConstructor
public class TripController {

    private final AdminTripService tripService;

    @GetMapping
    public ApiResponse<PageResult<TripPlanView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId) {
        return ApiResponse.success(tripService.listTrips(page, size, keyword, status, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<TripDetailDTO> detail(@PathVariable String id) {
        return ApiResponse.success(tripService.getTripDetail(id));
    }

    @AdminLog(module = "trip", action = "delete", targetType = "trip")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        tripService.deleteTrip(id);
        return ApiResponse.success(null, "删除成功");
    }
}
