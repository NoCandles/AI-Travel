package com.ai.travel.controller;

import com.ai.travel.dto.CityGroupDTO;
import com.ai.travel.entity.City;
import com.ai.travel.service.CityService;
import com.ai.travel.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    /** 获取所有城市列表*/
    @GetMapping("/list")
    public ApiResponse<List<City>> getAllCities() {
        return ApiResponse.success(cityService.getAllCities());
    }

    /** 获取热门城市列表 */
    @GetMapping("/hot")
    public ApiResponse<List<City>> getHotCities() {
        return ApiResponse.success(cityService.getHotCities());
    }

    /** 获取分组城市数据（热门+ A-Z字母分组：*/
    @GetMapping("/grouped")
    public ApiResponse<CityGroupDTO> getGroupedCities() {
        return ApiResponse.success(cityService.getGroupedCities());
    }

    /** 根据经纬度查找最近城市*/
    @GetMapping("/nearest")
    public ApiResponse<City> getNearestCity(@RequestParam double lng, @RequestParam double lat) {
        return ApiResponse.success(cityService.getNearestCity(lng, lat));
    }

    /** 根据城市名称查询坐标（用于前端地理编码失败时的兜底） */
    @GetMapping("/coords")
    public ApiResponse<Map<String, Object>> getCityCoords(@RequestParam String name) {
        return ApiResponse.success(cityService.getCityCoords(name));
    }
}
