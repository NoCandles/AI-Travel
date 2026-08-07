package com.ai.travel.service;

import java.util.List;
import java.util.Map;

public interface AttractionService {

    /**
     * 搜索附近景点
     * 逻辑同酒店：查 MySQL 缓存 → 没有则调腾讯地图 API → 存库 → 返回
     * @param keyword 景点关键词，如"曾厝垵景点"
     */
    List<Map<String, Object>> searchAttractions(Double lat, Double lng, String city, Integer radius, String keyword);
}
