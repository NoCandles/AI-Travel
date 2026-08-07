package com.ai.travel.service;

import java.util.Map;

/**
 * 地图服务 - 代理腾讯地图 API 调用
 */
public interface MapService {

    /**
     * 逆地理编码（坐标转地址）
     */
    Map<String, Object> reverseGeocode(double longitude, double latitude);

    /**
     * 地理编码（地址转坐标）
     */
    Map<String, Object> geocode(String address, String city);

    /**
     * POI 搜索
     */
    Map<String, Object> searchPOI(String keyword, String city);

    /**
     * 路径规划
     * @param city 城市名（公交/骑行模式时用于 transit 参数）
     */
    Map<String, Object> getRoute(String origin, String destination, String waypoints, String mode, String city);

    /**
     * 天气查询
     */
    Map<String, Object> getWeather(double latitude, double longitude);

    /**
     * 附近酒店搜索
     */
    Map<String, Object> searchNearbyHotels(double longitude, double latitude, String city, int radius, int pageIndex);
}
