package com.ai.travel.service;

import com.ai.travel.dto.HikingProfileDTO;
import com.ai.travel.dto.ShardResult;

public interface AIService {

    /**
     * 4 分片并行 AI 生成行程
     */
    ShardResult generateShards(String destination, int days, String budget,
                               String preferences, String mustVisitPlaces,
                               String startDate, String endDate,
                               String startPoint, String endPoint, String travelMode,
                               String hotTravelData);

    /**
     * 4 分片并行 AI 生成行程（含徒步专属参数）
     */
    ShardResult generateShards(String destination, int days, String budget,
                               String preferences, String mustVisitPlaces,
                               String startDate, String endDate,
                               String startPoint, String endPoint, String travelMode,
                               String hotTravelData, HikingProfileDTO hikingProfile);

    String generateSpotDescription(String spotName, String category);

    /**
     * AI 生成行李清单
     * @param destination 目的地
     * @param days 出行天数
     * @param travelMode 出行方式（drive/walk/transit/bike）
     * @param weatherInfo 天气预报（可选）
     * @return JSON 格式的行李清单
     */
    String generatePackingList(String destination, int days, String travelMode, String weatherInfo);
}
