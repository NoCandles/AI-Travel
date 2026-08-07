package com.ai.travel.controller;

import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.service.MapService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/map")
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;

    /**
     * 逆地理编码（坐标转地址，     */
    @GetMapping("/reverse-geocode")
    public ApiResponse<Map<String, Object>> reverseGeocode(
            @RequestParam double longitude,
            @RequestParam double latitude) {
        Map<String, Object> result = mapService.reverseGeocode(longitude, latitude);
        return ApiResponse.success(result);
    }

    /**
     * 地理编码（地址转坐标）
     */
    @GetMapping("/geocode")
    public ApiResponse<Map<String, Object>> geocode(
            @RequestParam String address,
            @RequestParam(required = false, defaultValue = "") String city) {
        Map<String, Object> result = mapService.geocode(address, city);
        return ApiResponse.success(result);
    }

    /**
     * POI 搜索
     */
    @GetMapping("/search-poi")
    public ApiResponse<Map<String, Object>> searchPOI(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "") String city) {
        Map<String, Object> result = mapService.searchPOI(keyword, city);
        return ApiResponse.success(result);
    }

    /**
     * 路径规划
     */
    @GetMapping("/route")
    public ApiResponse<Map<String, Object>> getRoute(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(required = false, defaultValue = "") String waypoints,
            @RequestParam(required = false, defaultValue = "drive") String mode,
            @RequestParam(required = false, defaultValue = "") String city) {
        Map<String, Object> result = mapService.getRoute(origin, destination, waypoints, mode, city);
        return ApiResponse.success(result);
    }

    /**
     * 附近酒店搜索
     */
    @GetMapping("/hotel-search")
    public ApiResponse<Map<String, Object>> searchNearbyHotels(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(required = false, defaultValue = "") String city,
            @RequestParam(required = false, defaultValue = "3000") int radius,
            @RequestParam(required = false, defaultValue = "1") int pageIndex) {
        Map<String, Object> result = mapService.searchNearbyHotels(longitude, latitude, city, radius, pageIndex);
        return ApiResponse.success(result);
    }

    /**
     * 天气查询
     */
    @GetMapping("/weather")
    public ApiResponse<Map<String, Object>> getWeather(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        Map<String, Object> result = mapService.getWeather(latitude, longitude);
        return ApiResponse.success(result);
    }
}
