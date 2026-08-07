package com.ai.travel.service;

import java.util.List;
import java.util.Map;

public interface HotelService {

    /**
     * 搜索附近酒店
     * 1. 查 MySQL 中该位置附近是否有缓存数据
     * 2. 有则直接返回
     * 3. 没有则调腾讯地图 API，存库后返回
     * @param keyword 搜索关键词，如"曾厝垵酒店"，为空则默认"酒店"
     */
    List<Map<String, Object>> searchHotels(Double lat, Double lng, String city, Integer radius, String keyword);

    /**
     * 通用 POI 搜索（景点、美食等）
     * 直接调腾讯地图 API，暂不缓存
     */
    List<Map<String, Object>> searchPOI(String keyword, Double lat, Double lng, String city, Integer radius);
}
