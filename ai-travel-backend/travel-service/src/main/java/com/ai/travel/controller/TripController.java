package com.ai.travel.controller;

import com.ai.travel.config.ProgressEmitter;
import com.ai.travel.config.RequireLogin;
import com.ai.travel.config.UserContext;
import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.dto.CreateTripRequest;
import com.ai.travel.dto.TripDayDTO;
import com.ai.travel.dto.TripPlanDTO;
import com.ai.travel.dto.TripSpotDTO;
import com.ai.travel.dto.packing.PackingCategoryDTO;
import com.ai.travel.dto.packing.PackingItemDTO;
import com.ai.travel.dto.packing.PackingListResponse;
import com.ai.travel.entity.TripPlan;
import com.ai.travel.repository.TripPlanRepository;
import com.ai.travel.service.AIService;
import com.ai.travel.service.TripService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Slf4j
public class TripController {

    private final TripService tripService;
    private final ProgressEmitter progressEmitter;
    private final AIService aiService;
    private final ObjectMapper objectMapper;
    private final TripPlanRepository tripPlanRepository;

    @RequireLogin
    @PostMapping
    public ApiResponse<TripPlanDTO> createTrip(
            @RequestBody @Valid CreateTripRequest request) {
        String userId = UserContext.getCurrentUserId();
        TripPlanDTO trip = tripService.createTrip(userId, request);
        return ApiResponse.success(trip, "行程创建成功");
    }

    /**
     * SSE 实时进度推送，替代轮询
     */
    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getProgress(@PathVariable String id) {
        return progressEmitter.register(id);
    }

    @GetMapping("/{id}")
    public ApiResponse<TripPlanDTO> getTripById(@PathVariable String id) {
        TripPlanDTO trip = tripService.getTripById(id);
        if (trip == null) {
            return ApiResponse.error("行程不存在");
        }
        return ApiResponse.success(trip);
    }

    @RequireLogin
    @GetMapping
    public ApiResponse<List<TripPlanDTO>> getUserTrips() {
        String userId = UserContext.getCurrentUserId();
        List<TripPlanDTO> trips = tripService.getUserTrips(userId);
        return ApiResponse.success(trips);
    }

    @RequireLogin
    @GetMapping("/count")
    public ApiResponse<Long> getTripCount() {
        String userId = UserContext.getCurrentUserId();
        long count = tripService.getTripCount(userId);
        return ApiResponse.success(count);
    }

    @RequireLogin
    @PutMapping("/{id}")
    public ApiResponse<TripPlanDTO> updateTrip(
            @PathVariable String id,
            @RequestBody TripPlanDTO tripPlanDTO) {
        TripPlanDTO trip = tripService.updateTrip(id, tripPlanDTO);
        return ApiResponse.success(trip, "行程更新成功");
    }

    @RequireLogin
    @PutMapping("/{id}/save")
    public ApiResponse<TripPlanDTO> saveTrip(
            @PathVariable String id) {
        TripPlanDTO tripPlanDTO = new TripPlanDTO();
        tripPlanDTO.setStatus("SAVED");
        TripPlanDTO trip = tripService.updateTrip(id, tripPlanDTO);
        return ApiResponse.success(trip, "行程保存成功");
    }

    /**
     * 单天重新生成
     */
    @RequireLogin
    @PostMapping("/{tripId}/days/{dayNum}/regenerate")
    public ApiResponse<TripPlanDTO> regenerateDay(
            @PathVariable String tripId,
            @PathVariable int dayNum) {
        tripService.regenerateDay(tripId, dayNum);
        TripPlanDTO trip = tripService.getTripById(tripId);
        return ApiResponse.success(trip, "第" + dayNum + "天已重新规划");
    }

    // ==================== 重新生成行程 ====================

    @RequireLogin
    @PostMapping("/{id}/regenerate")
    public ApiResponse<TripPlanDTO> regenerateTrip(
            @PathVariable String id,
            @RequestBody(required = false) CreateTripRequest request) {
        TripPlanDTO trip = tripService.regenerateTrip(id, request);
        return ApiResponse.success(trip, "正在重新生成行程");
    }

    @RequireLogin
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTrip(
            @PathVariable String id) {
        tripService.deleteTrip(id);
        return ApiResponse.success(null, "行程删除成功");
    }

    // ==================== 景点 CRUD ====================

    @RequireLogin
    @PutMapping("/spots/{spotId}")
    public ApiResponse<TripSpotDTO> updateSpot(
            @PathVariable String spotId,
            @RequestBody TripSpotDTO spotDTO) {
        TripSpotDTO spot = tripService.updateSpot(spotId, spotDTO);
        return ApiResponse.success(spot, "景点更新成功");
    }

    @RequireLogin
    @DeleteMapping("/spots/{spotId}")
    public ApiResponse<Void> deleteSpot(@PathVariable String spotId) {
        tripService.deleteSpot(spotId);
        return ApiResponse.success(null, "景点删除成功");
    }

    /**
     * 精准替换单个景点（不动同天其它景点）。对话里"不想去XX换一个"走这里。
     */
    @RequireLogin
    @PostMapping("/{tripId}/spots/{spotId}/replace")
    public ApiResponse<TripSpotDTO> replaceSpot(
            @PathVariable String tripId,
            @PathVariable String spotId,
            @RequestBody(required = false) Map<String, String> body) {
        String hint = body != null ? body.get("hint") : null;
        TripSpotDTO newSpot = tripService.replaceSpot(tripId, spotId, hint != null ? hint : "");
        if (newSpot == null) {
            return ApiResponse.error("没找到合适的替代景点，换个说法试试");
        }
        return ApiResponse.success(newSpot, "已替换为「" + newSpot.getName() + "」");
    }

    @RequireLogin
    @PostMapping("/{tripId}/days/{dayId}/spots")
    public ApiResponse<TripSpotDTO> addSpot(
            @PathVariable String tripId,
            @PathVariable String dayId,
            @RequestBody TripSpotDTO spotDTO) {
        spotDTO.setTripDayId(dayId);
        TripSpotDTO spot = tripService.addSpot(spotDTO);
        return ApiResponse.success(spot, "景点添加成功");
    }

    // ==================== 天数信息更新 ====================

    @RequireLogin
    @PutMapping("/days/{dayId}")
    public ApiResponse<TripDayDTO> updateDay(
            @PathVariable String dayId,
            @RequestBody TripDayDTO dayDTO) {
        TripDayDTO day = tripService.updateDay(dayId, dayDTO);
        return ApiResponse.success(day, "天数信息更新成功");
    }

    // ==================== 行程版本管理 ====================

    @RequireLogin
    @PostMapping("/{id}/versions")
    public ApiResponse<Void> saveVersion(
            @PathVariable String id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String note = (body != null) ? body.getOrDefault("note", "手动保存") : "手动保存";
        tripService.saveTripVersion(id, note);
        return ApiResponse.success(null, "版本已保存");
    }

    @RequireLogin
    @GetMapping("/{id}/versions")
    public ApiResponse<List<TripPlanDTO>> getVersions(@PathVariable String id) {
        List<TripPlanDTO> versions = tripService.getTripVersions(id);
        return ApiResponse.success(versions);
    }

    @RequireLogin
    @PostMapping("/{id}/versions/{versionId}/restore")
    public ApiResponse<Void> restoreVersion(
            @PathVariable String id,
            @PathVariable String versionId) {
        tripService.restoreTripVersion(id, versionId);
        return ApiResponse.success(null, "版本已恢复");
    }

    // ==================== 景点排序 ====================

    @RequireLogin
    @PutMapping("/days/{dayId}/spots/reorder")
    public ApiResponse<Void> reorderSpots(
            @PathVariable String dayId,
            @RequestBody List<String> spotIds) {
        tripService.reorderSpots(dayId, spotIds);
        return ApiResponse.success(null, "排序已更新");
    }

    // ==================== 智能推荐目的在====================

    @RequireLogin
    @GetMapping("/recommend/destinations")
    public ApiResponse<List<String>> recommendDestinations() {
        String userId = UserContext.getCurrentUserId();
        List<String> destinations = tripService.recommendDestinations(userId);
        return ApiResponse.success(destinations);
    }

    /**
     * 开始行程（标记为ONGOING，
     */
    @RequireLogin
    @PostMapping("/{id}/start")
    public ApiResponse<TripPlanDTO> startTrip(
            @PathVariable String id) {
        String userId = UserContext.getCurrentUserId();
        TripPlanDTO trip = tripService.startTrip(id, userId);
        return ApiResponse.success(trip, "行程已开启");
    }

    /**
     * 结束行程（标记为 COMPLETED，
     */
    @RequireLogin
    @PostMapping("/{id}/complete")
    public ApiResponse<TripPlanDTO> completeTrip(
            @PathVariable String id) {
        String userId = UserContext.getCurrentUserId();
        TripPlanDTO trip = tripService.completeTrip(id, userId);
        return ApiResponse.success(trip, "行程已结束");
    }

    /**
     * 获取行李清单
     * 如果提供于tripId，直接从数据库读取已存储的清单
     * 如果没有提供 tripId，调用AI 生成（保持现有行为）
     */
    @GetMapping("/packing-list")
    public ApiResponse<List<PackingCategoryDTO>> getPackingList(
            @RequestParam(required = false) String tripId,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false, defaultValue = "3") int days,
            @RequestParam(defaultValue = "drive") String travelMode,
            @RequestParam(required = false) String weather) {
        try {
            // 如果提供于tripId，直接从数据库读号
            if (tripId != null && !tripId.isEmpty()) {
                TripPlan tripPlan = tripPlanRepository.selectById(tripId);
                if (tripPlan == null) {
                    return ApiResponse.error("行程不存在：tripId=" + tripId);
                }
                
                if (tripPlan.getPackingListJson() != null && !tripPlan.getPackingListJson().isEmpty()) {
                    // 从数据库读取已存储的清单
                    PackingListResponse resp = objectMapper.readValue(tripPlan.getPackingListJson(), PackingListResponse.class);
                    return ApiResponse.success(resp.getCategories());
                } else {
                    // 数据库中没有清单，调用AI 生成并存储
                    String jsonStr = aiService.generatePackingList(
                            tripPlan.getDestination(), 
                            calculateDays(tripPlan.getStartDate(), tripPlan.getEndDate()), 
                            tripPlan.getTravelMode() != null ? tripPlan.getTravelMode() : "drive", 
                            "");
                    if (jsonStr != null && !jsonStr.isEmpty()) {
                        // 存储到数据库
                        tripPlan.setPackingListJson(jsonStr);
                        tripPlanRepository.updateById(tripPlan);
                        // 返回生成的清单
                        PackingListResponse resp = objectMapper.readValue(jsonStr, PackingListResponse.class);
                        return ApiResponse.success(resp.getCategories());
                    }
                }
            }
            
            // 如果没有提供 tripId 或数据库中没有清单，调用 AI 生成（保持现有行为）
            if (destination == null || destination.isEmpty()) {
                return ApiResponse.error("请提供tripId 成destination 参数");
            }
            
            String jsonStr = aiService.generatePackingList(destination, days, travelMode, weather);
            PackingListResponse resp = objectMapper.readValue(jsonStr, PackingListResponse.class);
            return ApiResponse.success(resp.getCategories());
        } catch (Exception e) {
            log.error("行李清单获取失败", e);
            return ApiResponse.error("行李清单获取失败，" + e.getMessage());
        }
    }

    /**
     * 保存行李清单（用户编辑后，
     */
    @RequireLogin
    @PutMapping("/{id}/packing-list")
    public ApiResponse<Void> savePackingList(
            @PathVariable String id,
            @RequestBody List<PackingCategoryDTO> categories) {
        try {
            TripPlan tripPlan = tripPlanRepository.selectById(id);
            if (tripPlan == null) {
                return ApiResponse.error("行程不存在");
            }
            
            // 将清单数据序列化为JSON 存储
            PackingListResponse resp = new PackingListResponse();
            resp.setCategories(categories);
            String jsonStr = objectMapper.writeValueAsString(resp);
            
            tripPlan.setPackingListJson(jsonStr);
            tripPlanRepository.updateById(tripPlan);
            
            log.info("行李清单已保存：tripId={}", id);
            return ApiResponse.success(null, "清单保存成功");
        } catch (Exception e) {
            log.error("行李清单保存失败", e);
            return ApiResponse.error("清单保存失败");
        }
    }

    /**
     * 重新生成行李清单
     */
    @RequireLogin
    @PostMapping("/{id}/packing-list/regenerate")
    public ApiResponse<List<PackingCategoryDTO>> regeneratePackingList(
            @PathVariable String id) {
        try {
            TripPlan tripPlan = tripPlanRepository.selectById(id);
            if (tripPlan == null) {
                return ApiResponse.error("行程不存在");
            }
            
            // 调用 AI 重新生成
            String jsonStr = aiService.generatePackingList(
                    tripPlan.getDestination(), 
                    calculateDays(tripPlan.getStartDate(), tripPlan.getEndDate()), 
                    tripPlan.getTravelMode() != null ? tripPlan.getTravelMode() : "drive", 
                    "");
            
            if (jsonStr != null && !jsonStr.isEmpty()) {
                // 存储到数据库
                tripPlan.setPackingListJson(jsonStr);
                tripPlanRepository.updateById(tripPlan);
                
                // 返回生成的清单
                PackingListResponse resp = objectMapper.readValue(jsonStr, PackingListResponse.class);
                return ApiResponse.success(resp.getCategories(), "清单重新生成成功");
            } else {
                return ApiResponse.error("清单重新生成失败");
            }
        } catch (Exception e) {
            log.error("行李清单重新生成失败", e);
            return ApiResponse.error("清单重新生成失败");
        }
    }

    /**
     * 计算行程天数
     */
    private int calculateDays(String startDateStr, String endDateStr) {
        try {
            LocalDate start = LocalDate.parse(startDateStr.split(" ")[0]);
            LocalDate end = LocalDate.parse(endDateStr.split(" ")[0]);
            return (int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        } catch (Exception e) {
            return 3;
        }
    }

    // ==================== 当前行程（进行中：===========

    /**
     * 获取用户当前进行中的行程
     */
    @RequireLogin
    @GetMapping("/ongoing")
    public ApiResponse<TripPlanDTO> getOngoingTrip() {
        String userId = UserContext.getCurrentUserId();
        TripPlanDTO trip = tripService.getOngoingTrip(userId);
        if (trip == null) {
            return ApiResponse.success(null, "当前没有进行中的行程");
        }
        return ApiResponse.success(trip);
    }

    /**
     * 打卡到达景点
     */
    @RequireLogin
    @PostMapping("/spots/{spotId}/check-in")
    public ApiResponse<TripSpotDTO> checkInSpot(
            @PathVariable String spotId) {
        TripSpotDTO spot = tripService.checkInSpot(spotId);
        return ApiResponse.success(spot, "打卡成功，");
    }
}
