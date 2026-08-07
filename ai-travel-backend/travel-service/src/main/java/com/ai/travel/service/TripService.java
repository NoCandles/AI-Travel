package com.ai.travel.service;

import com.ai.travel.dto.CreateTripRequest;
import com.ai.travel.dto.TripDayDTO;
import com.ai.travel.dto.TripPlanDTO;
import com.ai.travel.dto.TripSpotDTO;

import java.util.List;

public interface TripService {

    TripPlanDTO createTrip(String userId, CreateTripRequest request);

    TripPlanDTO getTripById(String id);

    List<TripPlanDTO> getUserTrips(String userId);

    long getTripCount(String userId);

    TripPlanDTO updateTrip(String id, TripPlanDTO tripPlanDTO);

    void deleteTrip(String id);

    TripPlanDTO generateTripByAI(String userId, CreateTripRequest request);

    // 景点 CRUD
    TripSpotDTO updateSpot(String spotId, TripSpotDTO spotDTO);

    void deleteSpot(String spotId);

    TripSpotDTO addSpot(TripSpotDTO spotDTO);

    // 天数信息更新
    TripDayDTO updateDay(String dayId, TripDayDTO dayDTO);

    // 重新生成行程
    TripPlanDTO regenerateTrip(String tripId, CreateTripRequest request);

    // 单天重新生成
    void regenerateDay(String tripId, int dayNum);

    /**
     * 替换单个景点（精准替换，不动同天其它景点）。
     * @param tripId 行程ID
     * @param spotId 要替换的景点ID（从当前行程景点列表解析）
     * @param hint 替换提示（如原景点名 / 用户偏好，供 AI 生成同类替代）
     * @return 新景点信息（含真实坐标），前端用于回显
     */
    TripSpotDTO replaceSpot(String tripId, String spotId, String hint);

    // 行程版本管理
    void saveTripVersion(String tripId, String versionNote);
    List<TripPlanDTO> getTripVersions(String tripId);
    void restoreTripVersion(String tripId, String versionId);
    /** 保存版本并返回版本ID（供对话撤销使用） */
    String saveTripVersionReturningId(String tripId, String versionNote);

    // 景点排序
    void reorderSpots(String dayId, List<String> spotIds);

    // 基于历史推荐目的地
    List<String> recommendDestinations(String userId);

    /** 开始行程（标记为 ONGOING） */
    TripPlanDTO startTrip(String tripId, String userId);

    /** 结束行程（标记为 COMPLETED） */
    TripPlanDTO completeTrip(String tripId, String userId);

    /** 获取用户当前进行中的行程 */
    TripPlanDTO getOngoingTrip(String userId);

    /** 打卡到达景点 */
    TripSpotDTO checkInSpot(String spotId);
}
