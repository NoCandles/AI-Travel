package com.ai.travel.admin.controller;

import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.admin.dto.DashboardOverviewDTO;
import com.ai.travel.admin.dto.DashboardTrendDTO;
import com.ai.travel.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AdminDashboardService dashboardService;

    /**
     * 数据总览
     */
    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewDTO> overview() {
        return ApiResponse.success(dashboardService.overview());
    }

    /**
     * 近0天趋动     */
    @GetMapping("/trends")
    public ApiResponse<List<DashboardTrendDTO>> trends() {
        return ApiResponse.success(dashboardService.trends());
    }
}
