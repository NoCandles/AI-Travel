package com.ai.travel.controller;

import com.ai.travel.dto.ApiResponse;
import com.ai.travel.service.AttractionService;
import com.ai.travel.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;
    private final AttractionService attractionService;

    /**
     * 搜索附近酒店
     * @param keyword 搜索关键词，如"曾厝垵酒店"，为空则默认"酒店"
     */
    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> searchHotels(
            @RequestParam(required = false, defaultValue = "0") Double lat,
            @RequestParam(required = false, defaultValue = "0") Double lng,
            @RequestParam(required = false, defaultValue = "") String city,
            @RequestParam(required = false, defaultValue = "3000") Integer radius,
            @RequestParam(required = false, defaultValue = "") String keyword) {

        List<Map<String, Object>> hotels = hotelService.searchHotels(lat, lng, city, radius, keyword);
        return ApiResponse.success(hotels);
    }

    /**
     * 通用 POI 搜索（景点、美食等）
     * 直接调腾讯地图 API，暂不缓存
     */
    @GetMapping("/poi-search")
    public ApiResponse<List<Map<String, Object>>> searchPOI(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "0") Double lat,
            @RequestParam(required = false, defaultValue = "0") Double lng,
            @RequestParam(required = false, defaultValue = "") String city,
            @RequestParam(required = false, defaultValue = "5000") Integer radius) {

        List<Map<String, Object>> result = hotelService.searchPOI(keyword, lat, lng, city, radius);
        return ApiResponse.success(result);
    }

    /**
     * 搜索附近景点（有缓存，逻辑同酒店）
     * @param keyword 景点关键词，如"风景区"，多个关键词逗号分隔
     */
    @GetMapping("/attractions")
    public ApiResponse<List<Map<String, Object>>> searchAttractions(
            @RequestParam(required = false, defaultValue = "0") Double lat,
            @RequestParam(required = false, defaultValue = "0") Double lng,
            @RequestParam(required = false, defaultValue = "") String city,
            @RequestParam(required = false, defaultValue = "5000") Integer radius,
            @RequestParam(required = false, defaultValue = "") String keyword) {

        List<Map<String, Object>> result = attractionService.searchAttractions(lat, lng, city, radius, keyword);
        return ApiResponse.success(result);
    }
}
