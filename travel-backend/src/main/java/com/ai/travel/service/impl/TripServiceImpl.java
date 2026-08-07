package com.ai.travel.service.impl;

import com.ai.travel.config.UserContext;
import com.ai.travel.dto.*;
import com.ai.travel.dto.packing.PackingCategoryDTO;
import com.ai.travel.dto.packing.PackingListResponse;
import com.ai.travel.entity.TripDay;
import com.ai.travel.entity.TripPlan;
import com.ai.travel.entity.TripPublish;
import com.ai.travel.entity.TripSpot;
import com.ai.travel.repository.TripDayRepository;
import com.ai.travel.repository.TripPlanRepository;
import com.ai.travel.repository.TripPublishRepository;
import com.ai.travel.repository.TripSpotRepository;
import com.ai.travel.service.PointsService;
import com.ai.travel.service.TripService;
import com.ai.travel.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripPlanRepository tripPlanRepository;
    private final TripDayRepository tripDayRepository;
    private final TripSpotRepository tripSpotRepository;
    private final TripPublishRepository tripPublishRepository;
    private final TripGenerationProcessor generationProcessor;
    private final PointsService pointsService;
    private final ObjectMapper objectMapper;

    @Value("${points.enabled:true}")
    private boolean pointsEnabled;

    @Override
    @Transactional
    public TripPlanDTO createTrip(String userId, CreateTripRequest request) {
        return generateTripByAI(userId, request);
    }

    @Override
    public TripPlanDTO getTripById(String id) {
        TripPlan tripPlan = tripPlanRepository.selectById(id);
        if (tripPlan == null) {
            return null;
        }
        
        // 权限校验：只有行程拥有者 或 该行程已公开发布（关联了已发布的广场文章）时可查看
        String currentUserId = UserContext.getCurrentUserId();
        boolean isOwner = currentUserId != null && currentUserId.equals(tripPlan.getUserId());
        if (!isOwner) {
            // 检查是否有关联的已发布广场文章
            QueryWrapper<TripPublish> publishQuery = new QueryWrapper<>();
            publishQuery.eq("trip_plan_id", id)
                        .eq("status", 1)
                        .eq("deleted", 0)
                        .last("LIMIT 1");
            TripPublish publish = tripPublishRepository.selectOne(publishQuery);
            if (publish == null) {
                throw new RuntimeException("无权访问该行程");
            }
        }
        
        return convertToDTO(tripPlan);
    }

    @Override
    public List<TripPlanDTO> getUserTrips(String userId) {
        QueryWrapper<TripPlan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.in("status", "SAVED", "ONGOING", "COMPLETED");
        queryWrapper.orderByDesc("created_at");
        List<TripPlan> tripPlans = tripPlanRepository.selectList(queryWrapper);
        return tripPlans.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public long getTripCount(String userId) {
        QueryWrapper<TripPlan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.in("status", "SAVED", "ONGOING", "COMPLETED");
        return tripPlanRepository.selectCount(queryWrapper);
    }

    @Override
    @Transactional
    public TripPlanDTO updateTrip(String id, TripPlanDTO tripPlanDTO) {
        TripPlan tripPlan = tripPlanRepository.selectById(id);
        if (tripPlan == null) {
            throw new RuntimeException("行程不存在");
        }

        // 校验用户权限：只能修改自己的行程
        String currentUserId = UserContext.getCurrentUserId();
        if (currentUserId != null && !currentUserId.equals("anonymous_user") && 
            !currentUserId.equals(tripPlan.getUserId())) {
            throw new RuntimeException("无权修改该行程");
        }

        if (tripPlanDTO.getName() != null) {
            tripPlan.setName(tripPlanDTO.getName());
        }
        if (tripPlanDTO.getDescription() != null) {
            tripPlan.setDescription(tripPlanDTO.getDescription());
        }
        if (tripPlanDTO.getStatus() != null) {
            tripPlan.setStatus(tripPlanDTO.getStatus());
        }

        // ✅ 处理行李清单更新（前端 packing-list 页保存时传 packingList）
        if (tripPlanDTO.getPackingList() != null) {
            try {
                PackingListResponse resp = new PackingListResponse();
                resp.setCategories(tripPlanDTO.getPackingList());
                String jsonStr = objectMapper.writeValueAsString(resp);
                tripPlan.setPackingListJson(jsonStr);
                log.info("行李清单已更新：tripId={}, itemsCount={}", id,
                    tripPlanDTO.getPackingList().stream().mapToInt(c -> c.getItems() != null ? c.getItems().size() : 0).sum());
            } catch (Exception e) {
                log.error("序列化 packingList 失败", e);
            }
        }

        tripPlanRepository.updateById(tripPlan);
        return convertToDTO(tripPlan);
    }

    @Override
    @Transactional
    public void deleteTrip(String id) {
        TripPlan tripPlan = tripPlanRepository.selectById(id);
        if (tripPlan == null) {
            throw new RuntimeException("行程不存在");
        }

        // 校验用户权限：只能删除自己的行程
        String currentUserId = UserContext.getCurrentUserId();
        if (currentUserId != null && !currentUserId.equals("anonymous_user") && 
            !currentUserId.equals(tripPlan.getUserId())) {
            throw new RuntimeException("无权删除该行程");
        }

        List<TripDay> days = tripDayRepository.findByTripId(id);
        for (TripDay day : days) {
            tripSpotRepository.delete(new QueryWrapper<TripSpot>().eq("trip_day_id", day.getId()));
        }
        tripDayRepository.delete(new QueryWrapper<TripDay>().eq("trip_id", id));
        tripPlanRepository.deleteById(id);
    }

    // ==================== 景点 CRUD ====================

    @Override
    @Transactional
    public TripSpotDTO updateSpot(String spotId, TripSpotDTO spotDTO) {
        TripSpot spot = tripSpotRepository.selectById(spotId);
        if (spot == null) {
            throw new RuntimeException("景点不存在");
        }

        if (spotDTO.getName() != null) spot.setName(spotDTO.getName());
        if (spotDTO.getCategory() != null) spot.setCategory(spotDTO.getCategory());
        if (spotDTO.getAddress() != null) spot.setAddress(spotDTO.getAddress());
        if (spotDTO.getLatitude() != null) spot.setLatitude(spotDTO.getLatitude());
        if (spotDTO.getLongitude() != null) spot.setLongitude(spotDTO.getLongitude());
        if (spotDTO.getArrivalTime() != null) spot.setArrivalTime(spotDTO.getArrivalTime());
        if (spotDTO.getDepartureTime() != null) spot.setDepartureTime(spotDTO.getDepartureTime());
        if (spotDTO.getDuration() != null) spot.setDuration(spotDTO.getDuration());
        if (spotDTO.getCost() != null) spot.setCost(spotDTO.getCost());
        if (spotDTO.getTips() != null) spot.setTips(spotDTO.getTips());
        if (spotDTO.getOrderNum() != null) spot.setOrderNum(spotDTO.getOrderNum());

        tripSpotRepository.updateById(spot);
        return convertSpotToDTO(spot);
    }

    @Override
    @Transactional
    public void deleteSpot(String spotId) {
        tripSpotRepository.deleteById(spotId);
    }

    @Override
    @Transactional
    public void batchUpdateSpotCoords(List<Map<String, Object>> coords) {
        if (coords == null || coords.isEmpty()) return;
        for (Map<String, Object> item : coords) {
            String spotId = (String) item.get("spotId");
            Object latObj = item.get("latitude");
            Object lngObj = item.get("longitude");
            if (spotId == null || latObj == null || lngObj == null) continue;

            double lat = ((Number) latObj).doubleValue();
            double lng = ((Number) lngObj).doubleValue();
            if (lat == 0 && lng == 0) continue;

            TripSpot spot = tripSpotRepository.selectById(spotId);
            if (spot != null) {
                spot.setLatitude(lat);
                spot.setLongitude(lng);
                tripSpotRepository.updateById(spot);
            }
        }
    }

    @Override
    @Transactional
    public TripSpotDTO addSpot(TripSpotDTO spotDTO) {
        TripSpot spot = new TripSpot();
        spot.setTripDayId(spotDTO.getTripDayId());
        spot.setName(spotDTO.getName());
        spot.setCategory(spotDTO.getCategory());
        spot.setAddress(spotDTO.getAddress());
        spot.setArrivalTime(spotDTO.getArrivalTime());
        spot.setDepartureTime(spotDTO.getDepartureTime());
        spot.setDuration(spotDTO.getDuration());
        spot.setCost(spotDTO.getCost());
        spot.setTips(spotDTO.getTips());

        // 自动设置 orderNum：当前 day 下最大 orderNum + 1
        List<TripSpot> existingSpots = tripSpotRepository.findByTripDayId(spotDTO.getTripDayId());
        int maxOrder = existingSpots.stream().mapToInt(s -> s.getOrderNum() != null ? s.getOrderNum() : 0).max().orElse(0);
        spot.setOrderNum(maxOrder + 1);

        tripSpotRepository.insert(spot);
        return convertSpotToDTO(spot);
    }

    // ==================== 天数信息更新 ====================

    @Override
    @Transactional
    public TripDayDTO updateDay(String dayId, TripDayDTO dayDTO) {
        TripDay day = tripDayRepository.selectById(dayId);
        if (day == null) {
            throw new RuntimeException("天数信息不存在");
        }

        if (dayDTO.getWeather() != null) day.setWeather(dayDTO.getWeather());
        if (dayDTO.getTemperature() != null) day.setTemperature(dayDTO.getTemperature());
        if (dayDTO.getNotes() != null) day.setNotes(dayDTO.getNotes());

        if (dayDTO.getHotel() != null) {
            day.setHotelName(dayDTO.getHotel().getName());
            day.setHotelDetail(dayDTO.getHotel().getDetail());
            if (dayDTO.getHotel().getPrice() != null) {
                day.setHotelPrice(dayDTO.getHotel().getPrice());
            }
        }

        tripDayRepository.updateById(day);
        return convertDayToDTO(day);
    }

    @Override
    @Transactional
    public TripPlanDTO generateTripByAI(String userId, CreateTripRequest request) {
        // 0. 扣除积分（可通过 points.enabled=false 关闭）
        if (pointsEnabled) {
            int cost = pointsService.calculateAICost(userId);
            try {
                pointsService.spendPoints(userId, "ai_plan", null, "AI生成路线");
                log.info("积分扣除成功：userId={}, cost={}", userId, cost);
            } catch (RuntimeException e) {
                log.warn("积分不足：userId={}, cost={}, msg={}", userId, cost, e.getMessage());
                throw new RuntimeException("积分不足，需要 " + cost + " 积分。前往积分中心签到获取积分吧！");
            }
        }

        // 1. 先插入空行程，状态 GENERATING，立即返回
        TripPlan tripPlan = new TripPlan();
        tripPlan.setUserId(userId);
        tripPlan.setDestination(request.getDestination());
        tripPlan.setStartDate(request.getStartDate());
        tripPlan.setEndDate(request.getEndDate());
        tripPlan.setBudget(request.getBudget());
        tripPlan.setName(request.getDestination() + " " + request.getDays() + "日游");
        tripPlan.setStatus("GENERATING");
        tripPlan.setPreferences(request.getPreferences() != null ?
                String.join(",", request.getPreferences()) : "");
        tripPlan.setMustVisitPlaces(request.getMustVisitPlaces() != null ?
                String.join(",", request.getMustVisitPlaces()) : "");
        tripPlan.setStartPoint(request.getStartPoint());
        tripPlan.setEndPoint(request.getEndPoint());
        tripPlan.setTravelMode(request.getTravelMode());

        tripPlanRepository.insert(tripPlan);
        log.info("行程已创建，状态 GENERATING：tripId={}", tripPlan.getId());

        // 2. 异步后台生成 AI 行程
        generationProcessor.processGeneration(tripPlan.getId(), request);

        // 3. 立即返回
        return convertToDTO(tripPlan);
    }

    @Override
    public void regenerateDay(String tripId, int dayNum) {
        generationProcessor.regenerateDay(tripId, dayNum);
    }

    private TripPlanDTO convertToDTO(TripPlan tripPlan) {
        TripPlanDTO dto = new TripPlanDTO();
        dto.setId(tripPlan.getId());
        dto.setName(tripPlan.getName());
        dto.setDestination(tripPlan.getDestination());
        dto.setStartDate(tripPlan.getStartDate());
        dto.setEndDate(tripPlan.getEndDate());
        dto.setDescription(tripPlan.getDescription());
        dto.setStatus(tripPlan.getStatus());
        dto.setUserId(tripPlan.getUserId());
        dto.setBudget(tripPlan.getBudget());
        dto.setTotalDistance(tripPlan.getTotalDistance());

        if (tripPlan.getPreferences() != null) {
            dto.setPreferences(List.of(tripPlan.getPreferences().split(",")));
        }
        if (tripPlan.getMustVisitPlaces() != null) {
            dto.setMustVisitPlaces(List.of(tripPlan.getMustVisitPlaces().split(",")));
        }

        List<TripDay> tripDays = tripDayRepository.findByTripId(tripPlan.getId());
        // 批量加载所有 days 对应的 spots，避免 N+1
        Map<String, List<TripSpot>> spotsMap = Collections.emptyMap();
        if (!tripDays.isEmpty()) {
            List<String> dayIds = tripDays.stream().map(TripDay::getId).collect(Collectors.toList());
            List<TripSpot> allSpots = tripSpotRepository.selectList(
                    new QueryWrapper<TripSpot>().in("trip_day_id", dayIds).orderByAsc("order_num"));
            spotsMap = allSpots.stream().collect(Collectors.groupingBy(TripSpot::getTripDayId));
        }
        List<TripDayDTO> dayDTOs = new ArrayList<>();
        for (TripDay tripDay : tripDays) {
            TripDayDTO dayDTO = convertDayToDTO(tripDay, spotsMap);
            dayDTOs.add(dayDTO);
        }
        dto.setDays(dayDTOs);

        // 解析多路线方案 routes_json
        if (tripPlan.getRoutesJson() != null && !tripPlan.getRoutesJson().isEmpty()) {
            try {
                JsonNode routesRoot = objectMapper.readTree(tripPlan.getRoutesJson());
                JsonNode routesArray = routesRoot.get("routes");
                if (routesArray != null && routesArray.isArray()) {
                    List<TripRouteDTO> routeList = new ArrayList<>();
                    for (JsonNode routeNode : routesArray) {
                        TripRouteDTO route = new TripRouteDTO();
                        route.setId(JsonUtils.getSafeText(routeNode, "id", ""));
                        route.setTitle(JsonUtils.getSafeText(routeNode, "title", ""));
                        route.setSubtitle(JsonUtils.getSafeText(routeNode, "subtitle", ""));

                        JsonNode daysNode = routeNode.get("days");
                        if (daysNode != null && daysNode.isArray()) {
                            List<TripDayDTO> routeDays = new ArrayList<>();
                            for (JsonNode dayNode : daysNode) {
                                TripDayDTO dayDTO = parseDayFromJson(dayNode);
                                if (dayDTO != null) {
                                    routeDays.add(dayDTO);
                                }
                            }
                            route.setDays(routeDays);
                        }
                        routeList.add(route);
                    }
                    dto.setRoutes(routeList);
                }
            } catch (Exception e) {
                log.warn("解析 routes_json 失败", e);
            }
        }

        // ✅ 解析行李清单 packingListJson → DTO
        if (tripPlan.getPackingListJson() != null && !tripPlan.getPackingListJson().isEmpty()) {
            try {
                PackingListResponse resp = objectMapper.readValue(tripPlan.getPackingListJson(), PackingListResponse.class);
                dto.setPackingList(resp.getCategories());
            } catch (Exception e) {
                log.warn("解析 packingListJson 失败", e);
            }
        }

        return dto;
    }

    /**
     * 从 JSON 节点解析 TripDayDTO（无数据库 id/tripId）
     */
    private TripDayDTO parseDayFromJson(JsonNode dayNode) {
        try {
            TripDayDTO dto = new TripDayDTO();
            dto.setDay(dayNode.has("day") ? dayNode.get("day").asInt() : 0);
            dto.setDate(JsonUtils.getSafeText(dayNode, "date", ""));
            dto.setWeather(JsonUtils.getSafeText(dayNode, "weather", ""));
            dto.setTemperature(JsonUtils.getSafeText(dayNode, "temperature", ""));
            dto.setNotes(JsonUtils.getSafeText(dayNode, "notes", ""));

            // 酒店
            JsonNode hotel = dayNode.get("hotel");
            if (hotel != null && !hotel.isNull()) {
                TripHotelDTO hotelDTO = new TripHotelDTO();
                hotelDTO.setName(JsonUtils.getSafeText(hotel, "name", ""));
                hotelDTO.setDetail(JsonUtils.getSafeText(hotel, "detail", ""));
                hotelDTO.setPrice(hotel.has("price") ? hotel.get("price").asInt() : 0);
                dto.setHotel(hotelDTO);
            }

            // 景点
            JsonNode points = dayNode.get("points");
            if (points != null && points.isArray()) {
                List<TripSpotDTO> spotList = new ArrayList<>();
                for (JsonNode pointNode : points) {
                    TripSpotDTO spot = new TripSpotDTO();
                    spot.setName(JsonUtils.getSafeText(pointNode, "name", ""));
                    spot.setCategory(JsonUtils.getSafeText(pointNode, "category", ""));
                    spot.setAddress(JsonUtils.getSafeText(pointNode, "address", ""));
                    spot.setOrderNum(pointNode.has("orderNum") ? pointNode.get("orderNum").asInt() : 0);
                    spot.setArrivalTime(JsonUtils.getSafeText(pointNode, "arrivalTime", ""));
                    spot.setDuration(pointNode.has("duration") ? String.valueOf(pointNode.get("duration").asInt()) : "0");
                    spot.setCost(JsonUtils.getSafeText(pointNode, "cost", ""));
                    spot.setTemperature(JsonUtils.getSafeText(pointNode, "temperature", ""));
                    spot.setImage(JsonUtils.getSafeText(pointNode, "image", ""));
                    spotList.add(spot);
                }
                dto.setSpots(spotList);
            }

            return dto;
        } catch (Exception e) {
            log.warn("解析 day JSON 失败", e);
            return null;
        }
    }

    private TripDayDTO convertDayToDTO(TripDay tripDay) {
        List<TripSpot> spots = tripSpotRepository.findByTripDayId(tripDay.getId());
        return buildDayDTO(tripDay, spots);
    }

    private TripDayDTO convertDayToDTO(TripDay tripDay, Map<String, List<TripSpot>> spotsMap) {
        List<TripSpot> spots = spotsMap.getOrDefault(tripDay.getId(), Collections.emptyList());
        return buildDayDTO(tripDay, spots);
    }

    private TripDayDTO buildDayDTO(TripDay tripDay, List<TripSpot> spots) {
        TripDayDTO dto = new TripDayDTO();
        dto.setId(tripDay.getId());
        dto.setTripId(tripDay.getTripId());
        dto.setDay(tripDay.getDay());
        dto.setDate(tripDay.getDate());
        dto.setWeather(tripDay.getWeather());
        dto.setTemperature(tripDay.getTemperature());
        dto.setNotes(tripDay.getNotes());

        if (tripDay.getHotelName() != null) {
            TripHotelDTO hotel = new TripHotelDTO();
            hotel.setName(tripDay.getHotelName());
            hotel.setDetail(tripDay.getHotelDetail());
            hotel.setPrice(tripDay.getHotelPrice());
            dto.setHotel(hotel);
        }

        List<TripSpotDTO> spotDTOs = spots.stream().map(this::convertSpotToDTO).collect(Collectors.toList());
        dto.setSpots(spotDTOs);

        return dto;
    }

    private TripSpotDTO convertSpotToDTO(TripSpot spot) {
        TripSpotDTO dto = new TripSpotDTO();
        dto.setId(spot.getId());
        dto.setTripDayId(spot.getTripDayId());
        dto.setName(spot.getName());
        dto.setCategory(spot.getCategory());
        dto.setAddress(spot.getAddress());
        dto.setLatitude(spot.getLatitude());
        dto.setLongitude(spot.getLongitude());
        dto.setOrderNum(spot.getOrderNum());
        dto.setArrivalTime(spot.getArrivalTime());
        dto.setDepartureTime(spot.getDepartureTime());
        dto.setDuration(spot.getDuration());
        dto.setCost(spot.getCost());
        dto.setTips(spot.getTips());
        dto.setTemperature(spot.getTemperature());
        dto.setImage(spot.getImage());
        dto.setIsReached(spot.getIsReached());
        dto.setReachedAt(spot.getReachedAt() != null ? spot.getReachedAt().toString() : null);
        return dto;
    }

    // ==================== 重新生成行程 ====================

    @Override
    @Transactional
    public TripPlanDTO regenerateTrip(String tripId, CreateTripRequest request) {
        TripPlan tripPlan = tripPlanRepository.selectById(tripId);
        if (tripPlan == null) {
            throw new RuntimeException("行程不存在");
        }

        // 保存当前版本（自动快照）
        saveTripVersion(tripId, "重新生成前自动备份");

        // 删除旧的 day 和 spot 数据
        List<TripDay> oldDays = tripDayRepository.findByTripId(tripId);
        for (TripDay day : oldDays) {
            tripSpotRepository.delete(new QueryWrapper<TripSpot>().eq("trip_day_id", day.getId()));
        }
        tripDayRepository.delete(new QueryWrapper<TripDay>().eq("trip_id", tripId));

        // 使用新参数或保留原参数重新生成
        if (request == null || request.getDays() == null) {
            if (request == null) {
                request = new CreateTripRequest();
            }
            request.setDestination(tripPlan.getDestination());
            request.setStartDate(tripPlan.getStartDate());
            request.setEndDate(tripPlan.getEndDate());
            request.setDays(calculateDaysBetween(tripPlan.getStartDate(), tripPlan.getEndDate()));
            request.setBudget(tripPlan.getBudget() != null ? tripPlan.getBudget() : "medium");
            if (tripPlan.getPreferences() != null && !tripPlan.getPreferences().isEmpty()) {
                request.setPreferences(List.of(tripPlan.getPreferences().split(",")));
            }
            if (tripPlan.getMustVisitPlaces() != null && !tripPlan.getMustVisitPlaces().isEmpty()) {
                request.setMustVisitPlaces(List.of(tripPlan.getMustVisitPlaces().split(",")));
            }
        }

        // 重置状态为 GENERATING，触发异步 AI 生成
        tripPlan.setStatus("GENERATING");
        tripPlanRepository.updateById(tripPlan);

        generationProcessor.processGeneration(tripId, request);

        return convertToDTO(tripPlan);
    }

    private int calculateDaysBetween(String start, String end) {
        try {
            java.time.LocalDate s = java.time.LocalDate.parse(start.split(" ")[0]);
            java.time.LocalDate e = java.time.LocalDate.parse(end.split(" ")[0]);
            return (int) java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1;
        } catch (Exception ex) {
            return 3;
        }
    }

    // ==================== 行程版本管理 ====================

    @Override
    @Transactional
    public void saveTripVersion(String tripId, String versionNote) {
        TripPlan tripPlan = tripPlanRepository.selectById(tripId);
        if (tripPlan == null) return;

        // 获取当前版本号
        int versionCount = 1 + countVersions(tripPlan);
        String versionName = "V" + versionCount;

        // 将当前行程数据序列化为 JSON 存入 routes_json 的版本字段
        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("versionId", "ver_" + System.currentTimeMillis());
            rootNode.put("versionName", versionName);
            rootNode.put("versionNote", versionNote != null ? versionNote : "");
            rootNode.put("createdAt", java.time.LocalDateTime.now().toString());

            // 序列化当前 days 数据
            ArrayNode daysArray = objectMapper.createArrayNode();
            List<TripDay> days = tripDayRepository.findByTripId(tripId);
            for (TripDay day : days) {
                ObjectNode dayNode = objectMapper.createObjectNode();
                dayNode.put("day", day.getDay());
                dayNode.put("date", day.getDate() != null ? day.getDate() : "");
                dayNode.put("weather", day.getWeather() != null ? day.getWeather() : "");
                dayNode.put("temperature", day.getTemperature() != null ? day.getTemperature() : "");

                // hotel
                if (day.getHotelName() != null || day.getHotelDetail() != null) {
                    ObjectNode hotelNode = objectMapper.createObjectNode();
                    hotelNode.put("name", day.getHotelName() != null ? day.getHotelName() : "");
                    hotelNode.put("detail", day.getHotelDetail() != null ? day.getHotelDetail() : "");
                    hotelNode.put("price", day.getHotelPrice() != null ? day.getHotelPrice() : 0);
                    dayNode.set("hotel", hotelNode);
                }

                // spots
                ArrayNode spotsArray = objectMapper.createArrayNode();
                List<TripSpot> spots = tripSpotRepository.findByTripDayId(day.getId());
                for (TripSpot spot : spots) {
                    ObjectNode spotNode = objectMapper.createObjectNode();
                    spotNode.put("name", spot.getName() != null ? spot.getName() : "");
                    spotNode.put("category", spot.getCategory() != null ? spot.getCategory() : "");
                    spotNode.put("address", spot.getAddress() != null ? spot.getAddress() : "");
                    spotNode.put("arrivalTime", spot.getArrivalTime() != null ? spot.getArrivalTime() : "");
                    spotNode.put("duration", spot.getDuration() != null ? spot.getDuration() : "0");
                    spotNode.put("cost", spot.getCost() != null ? spot.getCost() : "");
                    spotNode.put("tips", spot.getTips() != null ? spot.getTips() : "");
                    spotNode.put("orderNum", spot.getOrderNum() != null ? spot.getOrderNum() : 0);
                    spotsArray.add(spotNode);
                }
                dayNode.set("spots", spotsArray);
                daysArray.add(dayNode);
            }
            rootNode.set("days", daysArray);

            // 追加到 versions_json 字段
            String existingVersions = tripPlan.getVersionsJson();
            ArrayNode versionsArray;
            if (existingVersions != null && !existingVersions.isEmpty()) {
                JsonNode root = objectMapper.readTree(existingVersions);
                if (root.has("versions") && root.get("versions").isArray()) {
                    versionsArray = (ArrayNode) root.get("versions");
                } else {
                    versionsArray = objectMapper.createArrayNode();
                }
            } else {
                versionsArray = objectMapper.createArrayNode();
            }
            versionsArray.add(rootNode);

            ObjectNode finalRoot = objectMapper.createObjectNode();
            finalRoot.set("versions", versionsArray);
            tripPlan.setVersionsJson(objectMapper.writeValueAsString(finalRoot));
            tripPlanRepository.updateById(tripPlan);

            log.info("行程版本已保存：tripId={}, version={}", tripId, versionName);
        } catch (Exception e) {
            log.error("保存行程版本失败: tripId={}", tripId, e);
        }
    }

    private int countVersions(TripPlan tripPlan) {
        if (tripPlan == null || tripPlan.getVersionsJson() == null || tripPlan.getVersionsJson().isEmpty()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(tripPlan.getVersionsJson());
            if (root.has("versions") && root.get("versions").isArray()) {
                return root.get("versions").size();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    @Override
    public List<TripPlanDTO> getTripVersions(String tripId) {
        List<TripPlanDTO> versions = new ArrayList<>();
        TripPlan tripPlan = tripPlanRepository.selectById(tripId);
        if (tripPlan == null || tripPlan.getVersionsJson() == null || tripPlan.getVersionsJson().isEmpty()) {
            return versions;
        }
        try {
            JsonNode root = objectMapper.readTree(tripPlan.getVersionsJson());
            if (root.has("versions") && root.get("versions").isArray()) {
                for (JsonNode verNode : root.get("versions")) {
                    TripPlanDTO verDto = new TripPlanDTO();
                    verDto.setId(verNode.has("versionId") ? verNode.get("versionId").asText() : "");
                    verDto.setName(verNode.has("versionName") ? verNode.get("versionName").asText() : "");
                    verDto.setDescription(verNode.has("versionNote") ? verNode.get("versionNote").asText() : "");

                    // 解析版本中的 days
                    if (verNode.has("days") && verNode.get("days").isArray()) {
                        List<TripDayDTO> dayDtos = new ArrayList<>();
                        for (JsonNode dayNode : verNode.get("days")) {
                            TripDayDTO dayDto = parseDayFromJson(dayNode);
                            if (dayDto != null) dayDtos.add(dayDto);
                        }
                        verDto.setDays(dayDtos);
                    }
                    versions.add(verDto);
                }
            }
        } catch (Exception e) {
            log.error("获取行程版本列表失败", e);
        }
        return versions;
    }

    @Override
    @Transactional
    public void restoreTripVersion(String tripId, String versionId) {
        TripPlan tripPlan = tripPlanRepository.selectById(tripId);
        if (tripPlan == null) throw new RuntimeException("行程不存在");

        try {
            JsonNode root = objectMapper.readTree(tripPlan.getVersionsJson());
            if (!root.has("versions") || !root.get("versions").isArray()) {
                throw new RuntimeException("没有可恢复的版本");
            }

            JsonNode targetVersion = null;
            for (JsonNode ver : root.get("versions")) {
                if (versionId.equals(ver.has("versionId") ? ver.get("versionId").asText() : "")) {
                    targetVersion = ver;
                    break;
                }
            }
            if (targetVersion == null) throw new RuntimeException("版本不存在");

            // 先保存当前状态为新版本
            saveTripVersion(tripId, "恢复前自动备份");

            // 删除当前 days 和 spots
            List<TripDay> oldDays = tripDayRepository.findByTripId(tripId);
            for (TripDay day : oldDays) {
                tripSpotRepository.delete(new QueryWrapper<TripSpot>().eq("trip_day_id", day.getId()));
            }
            tripDayRepository.delete(new QueryWrapper<TripDay>().eq("trip_id", tripId));

            // 恢复版本数据到数据库
            if (targetVersion.has("days") && targetVersion.get("days").isArray()) {
                JsonNode daysArray = targetVersion.get("days");
                for (int i = 0; i < daysArray.size(); i++) {
                    JsonNode dayNode = daysArray.get(i);
                    TripDay day = new TripDay();
                    day.setTripId(tripId);
                    day.setDay(i + 1);
                    day.setDate(dayNode.has("date") ? dayNode.get("date").asText() : "");
                    day.setWeather(dayNode.has("weather") ? dayNode.get("weather").asText() : "");
                    day.setTemperature(dayNode.has("temperature") ? dayNode.get("temperature").asText() : "");

                    JsonNode hotel = dayNode.get("hotel");
                    if (hotel != null && !hotel.isNull()) {
                        day.setHotelName(hotel.has("name") ? hotel.get("name").asText() : "");
                        day.setHotelDetail(hotel.has("detail") ? hotel.get("detail").asText() : "");
                        day.setHotelPrice(hotel.has("price") ? hotel.get("price").asInt() : 0);
                    }
                    tripDayRepository.insert(day);

                    // 恢复景点
                    JsonNode spots = dayNode.get("spots");
                    if (spots != null && spots.isArray()) {
                        for (int j = 0; j < spots.size(); j++) {
                            JsonNode spotNode = spots.get(j);
                            TripSpot spot = new TripSpot();
                            spot.setTripDayId(day.getId());
                            spot.setName(spotNode.has("name") ? spotNode.get("name").asText() : "");
                            spot.setCategory(spotNode.has("category") ? spotNode.get("category").asText() : "");
                            spot.setAddress(spotNode.has("address") ? spotNode.get("address").asText() : "");
                            spot.setArrivalTime(spotNode.has("arrivalTime") ? spotNode.get("arrivalTime").asText() : "");
                            spot.setDuration(spotNode.has("duration") ? spotNode.get("duration").asText() : "0");
                            spot.setCost(spotNode.has("cost") ? spotNode.get("cost").asText() : "");
                            spot.setTips(spotNode.has("tips") ? spotNode.get("tips").asText() : "");
                            spot.setOrderNum(spotNode.has("orderNum") ? spotNode.get("orderNum").asInt() : j + 1);
                            tripSpotRepository.insert(spot);
                        }
                    }
                }
            }

            log.info("已恢复行程版本：tripId={}, versionId={}", tripId, versionId);
        } catch (Exception e) {
            log.error("恢复行程版本失败", e);
            throw new RuntimeException("恢复版本失败：" + e.getMessage());
        }
    }

    // ==================== 景点排序 ====================

    @Override
    @Transactional
    public void reorderSpots(String dayId, List<String> spotIds) {
        int order = 1;
        for (String spotId : spotIds) {
            TripSpot spot = tripSpotRepository.selectById(spotId);
            if (spot != null) {
                spot.setOrderNum(order++);
                tripSpotRepository.updateById(spot);
            }
        }
        log.info("景点排序已更新：dayId={}, spotCount={}", dayId, spotIds.size());
    }

    // ==================== 基于历史推荐目的地 ====================

    @Override
    public List<String> recommendDestinations(String userId) {
        QueryWrapper<TripPlan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                     .ne("destination", "")
                     .isNotNull("destination")
                     .select("DISTINCT destination")
                     .groupBy("destination")
                     .orderByDesc("created_at");

        List<TripPlan> trips = tripPlanRepository.selectList(queryWrapper);
        List<String> destinations = trips.stream()
                .map(TripPlan::getDestination)
                .filter(d -> d != null && !d.isEmpty())
                .distinct()
                .limit(8)
                .collect(Collectors.toList());

        log.info("推荐目的地：userId={}, destinations={}", userId, destinations);
        return destinations;
    }

    @Override
    @Transactional
    public TripPlanDTO startTrip(String tripId, String userId) {
        TripPlan tripPlan = tripPlanRepository.selectById(tripId);
        if (tripPlan == null) {
            throw new RuntimeException("行程不存在");
        }
        if (!tripPlan.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此行程");
        }
        if (!"SAVED".equals(tripPlan.getStatus())) {
            throw new RuntimeException("只有已保存的行程才能开始");
        }
        tripPlan.setStatus("ONGOING");
        tripPlanRepository.updateById(tripPlan);
        log.info("行程已开始：tripId={}, userId={}", tripId, userId);
        return convertToDTO(tripPlan);
    }

    @Override
    @Transactional
    public TripPlanDTO completeTrip(String tripId, String userId) {
        TripPlan tripPlan = tripPlanRepository.selectById(tripId);
        if (tripPlan == null) {
            throw new RuntimeException("行程不存在");
        }
        if (!tripPlan.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此行程");
        }
        tripPlan.setStatus("COMPLETED");
        tripPlanRepository.updateById(tripPlan);
        log.info("行程已结束：tripId={}, userId={}", tripId, userId);
        return convertToDTO(tripPlan);
    }

    @Override
    public TripPlanDTO getOngoingTrip(String userId) {
        QueryWrapper<TripPlan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                     .eq("status", "ONGOING")
                     .last("LIMIT 1");
        TripPlan tripPlan = tripPlanRepository.selectOne(queryWrapper);
        if (tripPlan == null) {
            return null;
        }
        log.info("获取当前进行中行程：userId={}, tripId={}", userId, tripPlan.getId());
        return convertToDTO(tripPlan);
    }

    @Override
    @Transactional
    public TripSpotDTO checkInSpot(String spotId) {
        TripSpot spot = tripSpotRepository.selectById(spotId);
        if (spot == null) {
            throw new RuntimeException("景点不存在");
        }
        spot.setIsReached(true);
        spot.setReachedAt(LocalDateTime.now());
        tripSpotRepository.updateById(spot);
        log.info("景点打卡到达：spotId={}, name={}", spotId, spot.getName());
        return convertSpotToDTO(spot);
    }
}
