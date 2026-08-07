package com.ai.travel.service.impl;

import com.ai.travel.config.ProgressEmitter;
import com.ai.travel.dto.CreateTripRequest;
import com.ai.travel.dto.HikingProfileDTO;
import com.ai.travel.dto.ShardResult;
import com.ai.travel.entity.HikingRoute;
import com.ai.travel.entity.HikingSegment;
import com.ai.travel.entity.TripDay;
import com.ai.travel.entity.TripPlan;
import com.ai.travel.entity.TripSpot;
import com.ai.travel.repository.HikingRouteRepository;
import com.ai.travel.repository.HikingSegmentRepository;
import com.ai.travel.repository.TripDayRepository;
import com.ai.travel.repository.TripPlanRepository;
import com.ai.travel.repository.TripSpotRepository;
import com.ai.travel.service.AIService;
import com.ai.travel.service.HotDataService;
import com.ai.travel.service.MapService;
import com.ai.travel.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripGenerationProcessor {

    private final AIService aiService;
    private final HotDataService hotDataService;
    private final TripPlanRepository tripPlanRepository;
    private final TripDayRepository tripDayRepository;
    private final TripSpotRepository tripSpotRepository;
    private final HikingRouteRepository hikingRouteRepository;
    private final HikingSegmentRepository hikingSegmentRepository;
    private final ImageServiceImpl imageService;
    private final MapService mapService;
    private final ProgressEmitter progressEmitter;
    private final ObjectMapper objectMapper;

    @Async("tripGenerationExecutor")
    public void processGeneration(String tripId, CreateTripRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 开始多路线并行生成行程：tripId={}", tripId);
        progressEmitter.send(tripId, "progress", Map.of("stage", "start", "message", "AI 开始规划路线..."));
        try {
            String preferences = request.getPreferences() != null ?
                    String.join(",", request.getPreferences()) : "";
            String mustVisit = request.getMustVisitPlaces() != null ?
                    String.join(",", request.getMustVisitPlaces()) : "";

            // 1. 获取热点数据
            String month = extractMonth(request.getStartDate());
            String hotTravelData = hotDataService.getHotTravelData(request.getDestination(), month + "月");
            if (!hotTravelData.isEmpty()) {
                log.info("热点数据已注入：tripId={}, dataLength={}", tripId, hotTravelData.length());
            } else {
                log.info("热点数据为空，继续生成：tripId={}", tripId);
            }

            // 1.5 获取真实天气预报，注入 Prompt
            String weatherSection = fetchWeather(request.getDestination(), request.getStartDate(), request.getDays());
            if (!weatherSection.isEmpty()) {
                hotTravelData = hotTravelData + weatherSection;
            }

            // 2. 4 分片并行 AI 生成（长行程自动分块）
            long aiStartTime = System.currentTimeMillis();
            ShardResult shardResult;
            HikingProfileDTO hikingProfile = request.getHikingProfile();
            
            // 对于 8 天以上的行程，分块生成以避免 AI 截断
            int totalDays = request.getDays();
            if (totalDays > 7 && !"hiking".equals(request.getTravelMode())) {
                shardResult = generateInChunks(request, preferences, mustVisit, hotTravelData, totalDays);
            } else if ("hiking".equals(request.getTravelMode()) && hikingProfile != null) {
                shardResult = aiService.generateShards(
                        request.getDestination(), totalDays,
                        request.getBudget(), preferences, mustVisit,
                        request.getStartDate(), request.getEndDate(),
                        request.getStartPoint(), request.getEndPoint(),
                        request.getTravelMode(), hotTravelData, hikingProfile);
            } else {
                shardResult = aiService.generateShards(
                        request.getDestination(), totalDays,
                        request.getBudget(), preferences, mustVisit,
                        request.getStartDate(), request.getEndDate(),
                        request.getStartPoint(), request.getEndPoint(),
                        request.getTravelMode(), hotTravelData);
            }
            log.info("⏱ 4 分片 AI 调用完成，耗时={}秒", (System.currentTimeMillis() - aiStartTime) / 1000.0);
            progressEmitter.send(tripId, "progress", Map.of("stage", "ai_done", "message", "AI 生成完成，正在整理路线..."));

            log.info("4 分片生成完成，开始组装路线：tripId={}", tripId);

            // 3. 解析 4 个分片
            JsonNode shard1Json = parseJsonSafe(shardResult.getShard1());
            JsonNode shard2Json = parseJsonSafe(shardResult.getShard2());
            JsonNode shard3Json = parseJsonSafe(shardResult.getShard3());
            JsonNode shard4Json = parseJsonSafe(shardResult.getShard4());

            if (shard1Json == null || shard2Json == null || shard3Json == null || shard4Json == null) {
                log.error("分片 JSON 解析失败，创建默认行程：tripId={}", tripId);
                createDefaultTrip(tripId, request);
                return;
            }

            // 4. 提取每个分片的 days 数组
            int targetDays = request.getDays();
            List<JsonNode> shard1Days = extractDays(shard1Json, targetDays);
            List<JsonNode> shard2Days = extractDays(shard2Json, targetDays);
            List<JsonNode> shard3Days = extractDays(shard3Json, targetDays);
            // Shard4 有 3 套风格
            Map<String, List<JsonNode>> shard4Styles = extractShard4Styles(shard4Json, targetDays);

            // 校验 AI 返回的天数是否完整
            log.info("📊 天数完整性检查: target={}, shard1={}, shard2={}, shard3={}",
                    targetDays, shard1Days.size(), shard2Days.size(), shard3Days.size());
            if (shard1Days.size() < targetDays) {
                log.warn("⚠️ Shard1(时令定制版) 天数不足：期望{}天, AI仅返回{}天, 已用空数据补齐", targetDays, shard1Days.size());
            }
            if (shard2Days.size() < targetDays) {
                log.warn("⚠️ Shard2(网红爆款版) 天数不足：期望{}天, AI仅返回{}天, 已用空数据补齐", targetDays, shard2Days.size());
            }
            if (shard3Days.size() < targetDays) {
                log.warn("⚠️ Shard3(经典稳妥版) 天数不足：期望{}天, AI仅返回{}天, 已用空数据补齐", targetDays, shard3Days.size());
            }

            // 5. 组装 3 条路线
            boolean isHiking = "hiking".equals(request.getTravelMode());
            String titleSeasonal, subtitleSeasonal, titleTrendy, subtitleTrendy, titleClassic, subtitleClassic;
            if (isHiking) {
                titleSeasonal = "🌿 风景休闲徒步";
                subtitleSeasonal = "以自然风光为主线，沿途最佳观景点，路线平缓舒适";
                titleTrendy = "🔥 探险挑战徒步";
                subtitleTrendy = "原生态路段+特色地貌，适合喜欢探索的徒步爱好者";
                titleClassic = "⭐ 经典环线徒步";
                subtitleClassic = "起点终点相同，路线成熟，适合大多数徒步爱好者";
            } else {
                titleSeasonal = "🌸 当月时令定制版";
                subtitleSeasonal = "根植于" + month + "月的时令特色，应季景点 + 特色活动";
                titleTrendy = "🔥 当下网红爆款版";
                subtitleTrendy = "社交媒体最新热门打卡路线";
                titleClassic = "⭐ 经典稳妥路线";
                subtitleClassic = "经典必去景点，成熟旅行方案";
            }

            // 路线 A：Shard1 行程 + Shard4.seasonal 食宿
            List<JsonNode> routeADays = mergeDaysWithAccommodation(
                    shard1Days, shard4Styles.getOrDefault("seasonal", shard1Days), request.getDays(), request.getStartDate());

            // 路线 B：Shard2 行程 + Shard4.trendy 食宿
            List<JsonNode> routeBDays = mergeDaysWithAccommodation(
                    shard2Days, shard4Styles.getOrDefault("trendy", shard2Days), request.getDays(), request.getStartDate());

            // 路线 C：Shard3 行程 + Shard4.classic 食宿
            List<JsonNode> routeCDays = mergeDaysWithAccommodation(
                    shard3Days, shard4Styles.getOrDefault("classic", shard3Days), request.getDays(), request.getStartDate());

            // 6. 去重（同一路线内相邻天重复景点只保留第一次）
            routeADays = deduplicateSpots(routeADays);
            routeBDays = deduplicateSpots(routeBDays);
            routeCDays = deduplicateSpots(routeCDays);

            // 7. 先写入路线A + 设置 DRAFT（前端立即看到第一条路线）
            String routesJsonA = buildSingleRouteJson(titleSeasonal, subtitleSeasonal, routeADays);
            saveRouteToTables(tripId, request, routeADays, false);
            TripPlan tripPlan = tripPlanRepository.selectById(tripId);
            if (tripPlan != null) {
                tripPlan.setName(request.getDestination() + " " + request.getDays() + "日游");
                tripPlan.setStatus("DRAFT");
                tripPlan.setRoutesJson(routesJsonA);
                tripPlanRepository.updateById(tripPlan);
            }
            progressEmitter.send(tripId, "progress", Map.of("stage", "routeA", "message", "第1条路线已生成，正在规划更多方案..."));

            // 8. 构建完整 3 条路线的 routes_json（追加 B、C）
            String routesJson = buildRoutesJson(titleSeasonal, subtitleSeasonal, routeADays,
                    titleTrendy, subtitleTrendy, routeBDays,
                    titleClassic, subtitleClassic, routeCDays);
            tripPlan.setRoutesJson(routesJson);
            tripPlanRepository.updateById(tripPlan);

            log.info("3 条路线组装完成，routes_json 长度：{}", routesJson.length());

            // 10. 徒步模式：写入 hiking_route + hiking_segment 表
            if (isHiking) {
                saveHikingRoutes(tripId, request, routeADays, routeBDays, routeCDays,
                        shard1Json, shard2Json, shard3Json, shard4Json);
            }

            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            List<TripDay> savedDays = tripDayRepository.findByTripId(tripId);
            log.info("🎉 行程生成完成：tripId={}, 天数={}, status=DRAFT, 耗时={}秒",
                    tripId, savedDays.size(), elapsed);

            // 9.5. 生成行李清单并存储（不阻塞主流程）
            generateAndSavePackingList(tripId, request);

            progressEmitter.sendComplete(tripId);

            // 11. 异步拉取景点图片（不阻塞主流程）
            fetchImagesAsync(tripId);

        } catch (Exception e) {
            log.error("❌ 异步生成行程失败：tripId={}, 耗时={}秒", tripId,
                    (System.currentTimeMillis() - startTime) / 1000.0, e);
            progressEmitter.sendError(tripId, "生成失败：" + e.getMessage());
            TripPlan tripPlan = tripPlanRepository.selectById(tripId);
            if (tripPlan != null) {
                tripPlan.setStatus("FAILED");
                tripPlan.setDescription("生成失败：" + e.getMessage());
                tripPlanRepository.updateById(tripPlan);
            }
        }
    }

    // ==================== 分片解析 ====================

    /**
     * 安全解析 JSON，失败返回 null
     */
    private JsonNode parseJsonSafe(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从分片 JSON 中提取 days 数组，按目标天数对齐
     */
    private List<JsonNode> extractDays(JsonNode root, int targetDays) {
        List<JsonNode> result = new ArrayList<>();
        if (root == null) return padDays(result, targetDays);

        try {
            JsonNode days = root.get("days");
            if (days != null && days.isArray()) {
                for (int i = 0; i < days.size() && i < targetDays; i++) {
                    result.add(days.get(i));
                }
                if (days.size() < targetDays) {
                    log.warn("extractDays: AI 返回天数不足，期望{}天，实际{}天", targetDays, days.size());
                }
            } else {
                log.warn("extractDays: days 节点不存在或不是数组");
            }
        } catch (Exception e) {
            log.warn("提取 days 失败：{}", e.getMessage());
        }

        return padDays(result, targetDays);
    }

    /**
     * 天数不足时用默认数据补齐
     */
    private List<JsonNode> padDays(List<JsonNode> days, int targetDays) {
        while (days.size() < targetDays) {
            ObjectNode emptyDay = objectMapper.createObjectNode();
            emptyDay.put("day", days.size() + 1);
            emptyDay.put("weather", "晴");
            emptyDay.put("temperature", "25°C");
            emptyDay.set("points", objectMapper.createArrayNode());
            ObjectNode hotel = objectMapper.createObjectNode();
            hotel.put("name", "");
            hotel.put("detail", "");
            hotel.put("price", 0);
            emptyDay.set("hotel", hotel);
            days.add(emptyDay);
        }
        return days;
    }

    /**
     * 提取 Shard4 的 3 套风格食宿
     */
    private Map<String, List<JsonNode>> extractShard4Styles(JsonNode root, int targetDays) {
        Map<String, List<JsonNode>> result = new HashMap<>();
        if (root == null) return result;

        for (String style : Arrays.asList("seasonal", "trendy", "classic")) {
            JsonNode styleNode = root.get(style);
            if (styleNode != null) {
                List<JsonNode> days = extractDays(styleNode, targetDays);
                result.put(style, days);
            }
        }
        return result;
    }

    // ==================== 路线组装 ====================

    /**
     * 合并行程 + 食宿，以行程为主，用食宿的 hotel 覆盖
     */
    private List<JsonNode> mergeDaysWithAccommodation(List<JsonNode> itineraryDays,
                                                       List<JsonNode> accommodationDays,
                                                       int targetDays, String startDate) {
        List<JsonNode> result = new ArrayList<>();
        LocalDate start = parseDate(startDate);

        for (int i = 0; i < targetDays; i++) {
            try {
                ObjectNode merged = objectMapper.createObjectNode();
                JsonNode itDay = i < itineraryDays.size() ? itineraryDays.get(i) : null;
                JsonNode accDay = i < accommodationDays.size() ? accommodationDays.get(i) : null;

                // 基本信息
                if (itDay != null) {
                    merged.put("day", itDay.has("day") ? itDay.get("day").asInt() : i + 1);
                    merged.put("date", itDay.has("date") ? itDay.get("date").asText() : start.plusDays(i).format(DateTimeFormatter.ISO_DATE));
                    merged.put("weather", itDay.has("weather") ? itDay.get("weather").asText() : "晴");
                    merged.put("temperature", itDay.has("temperature") ? itDay.get("temperature").asText() : "25°C");
                } else {
                    merged.put("day", i + 1);
                    merged.put("date", start.plusDays(i).format(DateTimeFormatter.ISO_DATE));
                    merged.put("weather", "晴");
                    merged.put("temperature", "25°C");
                }

                // 景点（优先用行程数据）
                if (itDay != null && itDay.has("points") && itDay.get("points").isArray()) {
                    merged.set("points", itDay.get("points"));
                } else {
                    merged.set("points", objectMapper.createArrayNode());
                }

                // 酒店（优先用食宿数据，次选行程数据）
                JsonNode hotelNode = null;
                if (accDay != null && accDay.has("hotel") && !accDay.get("hotel").isNull()
                        && accDay.get("hotel").has("name") && !accDay.get("hotel").get("name").asText().isEmpty()) {
                    hotelNode = accDay.get("hotel");
                } else if (itDay != null && itDay.has("hotel") && !itDay.get("hotel").isNull()) {
                    hotelNode = itDay.get("hotel");
                }

                if (hotelNode != null) {
                    merged.set("hotel", hotelNode);
                } else {
                    ObjectNode defaultHotel = objectMapper.createObjectNode();
                    defaultHotel.put("name", "");
                    defaultHotel.put("detail", "");
                    defaultHotel.put("price", 0);
                    merged.set("hotel", defaultHotel);
                }

                // 美食备注（来自食宿数据）
                if (accDay != null && accDay.has("food")) {
                    merged.put("food", accDay.get("food").asText(""));
                }

                result.add(merged);
            } catch (Exception e) {
                log.warn("合并天数失败 day={}", i + 1, e);
            }
        }
        return result;
    }

    /**
     * 去重：同一路线内，相邻天出现同名景点只保留第一次
     */
    private List<JsonNode> deduplicateSpots(List<JsonNode> days) {
        Set<String> seenSpotNames = new HashSet<>();

        for (JsonNode day : days) {
            if (!day.has("points") || !day.get("points").isArray()) continue;

            ArrayNode deduped = objectMapper.createArrayNode();
            for (JsonNode spot : day.get("points")) {
                String name = spot.has("name") ? spot.get("name").asText("").trim() : "";
                if (name.isEmpty() || !seenSpotNames.contains(name)) {
                    if (!name.isEmpty()) {
                        seenSpotNames.add(name);
                    }
                    deduped.add(spot);
                }
            }
            ((ObjectNode) day).set("points", deduped);
        }
        return days;
    }

    // ==================== JSON 构建 ====================

    /**
     * 构建单条路线的 routes_json（先返回路线A，不等待B/C）
     */
    private String buildSingleRouteJson(String title, String subtitle, List<JsonNode> days) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode routes = objectMapper.createArrayNode();
            routes.add(buildRouteNode("seasonal", title, subtitle, days));
            root.set("routes", routes);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"routes\":[]}";
        }
    }

    /**
     * 构建 3 条路线的 routes_json
     */
    private String buildRoutesJson(String title1, String sub1, List<JsonNode> days1,
                                   String title2, String sub2, List<JsonNode> days2,
                                   String title3, String sub3, List<JsonNode> days3) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode routes = objectMapper.createArrayNode();

            routes.add(buildRouteNode("seasonal", title1, sub1, days1));
            routes.add(buildRouteNode("trendy", title2, sub2, days2));
            routes.add(buildRouteNode("classic", title3, sub3, days3));

            root.set("routes", routes);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("构建 routes_json 失败", e);
            return "{\"routes\":[]}";
        }
    }

    private ObjectNode buildRouteNode(String id, String title, String subtitle, List<JsonNode> days) {
        ObjectNode route = objectMapper.createObjectNode();
        route.put("id", id);
        route.put("title", title);
        route.put("subtitle", subtitle);

        ArrayNode daysArray = objectMapper.createArrayNode();
        for (JsonNode day : days) {
            daysArray.add(day);
        }
        route.set("days", daysArray);

        return route;
    }

    // ==================== 分块生成（长行程） ====================

    /**
     * 长行程（>7天）分块生成，每块最多5天，避免 AI 截断
     */
    private ShardResult generateInChunks(CreateTripRequest request, String preferences,
                                          String mustVisit, String hotTravelData, int totalDays) {
        int chunkSize = 5;  // 每块最多5天
        int numChunks = (totalDays + chunkSize - 1) / chunkSize;
        log.info("📦 长行程分块生成：totalDays={}, chunkSize={}, numChunks={}", totalDays, chunkSize, numChunks);

        List<String> allShard1 = new ArrayList<>();
        List<String> allShard2 = new ArrayList<>();
        List<String> allShard3 = new ArrayList<>();
        List<String> allShard4 = new ArrayList<>();

        for (int chunk = 0; chunk < numChunks; chunk++) {
            int chunkStart = chunk * chunkSize + 1;
            int chunkEnd = Math.min((chunk + 1) * chunkSize, totalDays);
            int chunkDays = chunkEnd - chunkStart + 1;

            // 计算分块的日期
            LocalDate startDate = LocalDate.parse(request.getStartDate(), DateTimeFormatter.ISO_DATE);
            String chunkStartDate = startDate.plusDays(chunkStart - 1).format(DateTimeFormatter.ISO_DATE);
            String chunkEndDate = startDate.plusDays(chunkEnd - 1).format(DateTimeFormatter.ISO_DATE);

            log.info("📦 生成第{}块：day{}-day{} ({}天), {}-{}", 
                    chunk + 1, chunkStart, chunkEnd, chunkDays, chunkStartDate, chunkEndDate);

            ShardResult chunkShards = aiService.generateShards(
                    request.getDestination(), chunkDays,
                    request.getBudget(), preferences, mustVisit,
                    chunkStartDate, chunkEndDate,
                    chunkStart == 1 ? request.getStartPoint() : null,
                    chunkEnd == totalDays ? request.getEndPoint() : null,
                    request.getTravelMode(), hotTravelData);

            // 解析并调整 day 编号
            JsonNode s1 = parseJsonSafe(chunkShards.getShard1());
            JsonNode s2 = parseJsonSafe(chunkShards.getShard2());
            JsonNode s3 = parseJsonSafe(chunkShards.getShard3());
            JsonNode s4 = parseJsonSafe(chunkShards.getShard4());

            allShard1.add(reindexDays(s1, chunkStart));
            allShard2.add(reindexDays(s2, chunkStart));
            allShard3.add(reindexDays(s3, chunkStart));
            allShard4.add(reindexDays(s4, chunkStart));
        }

        // 合并所有分块
        LocalDate firstDate = LocalDate.parse(request.getStartDate(), DateTimeFormatter.ISO_DATE);
        return ShardResult.builder()
                .shard1(mergeChunkJsons(allShard1, totalDays, firstDate))
                .shard2(mergeChunkJsons(allShard2, totalDays, firstDate))
                .shard3(mergeChunkJsons(allShard3, totalDays, firstDate))
                .shard4(mergeChunkShard4(allShard4, totalDays))
                .build();
    }

    /**
     * 将分块 JSON 的 day 编号偏移到全局编号
     */
    private String reindexDays(JsonNode json, int offset) {
        if (json == null) return "{\"days\":[]}";
        try {
            if (json.has("days") && json.get("days").isArray()) {
                ArrayNode daysArr = (ArrayNode) json.get("days");
                for (int i = 0; i < daysArr.size(); i++) {
                    ObjectNode day = (ObjectNode) daysArr.get(i);
                    if (day.has("day")) {
                        day.put("day", day.get("day").asInt() + offset - 1);
                    }
                }
            }
            // Shard4 有三套风格
            for (String style : Arrays.asList("seasonal", "trendy", "classic")) {
                if (json.has(style)) {
                    JsonNode styleNode = json.get(style);
                    if (styleNode.has("days") && styleNode.get("days").isArray()) {
                        ArrayNode daysArr = (ArrayNode) styleNode.get("days");
                        for (int i = 0; i < daysArr.size(); i++) {
                            ObjectNode day = (ObjectNode) daysArr.get(i);
                            if (day.has("day")) {
                                day.put("day", day.get("day").asInt() + offset - 1);
                            }
                        }
                    }
                }
            }
            return json.toString();
        } catch (Exception e) {
            log.warn("reindexDays 失败：{}", e.getMessage());
            return json.toString();
        }
    }

    private String mergeChunkJsons(List<String> chunkJsons, int totalDays, LocalDate firstDate) {
        try {
            ArrayNode allDays = objectMapper.createArrayNode();
            int dayNum = 1;
            for (String chunkJson : chunkJsons) {
                JsonNode chunk = objectMapper.readTree(chunkJson);
                if (chunk.has("days") && chunk.get("days").isArray()) {
                    for (JsonNode day : chunk.get("days")) {
                        ObjectNode d = (ObjectNode) day;
                        d.put("day", dayNum);
                        if (!d.has("date") || d.get("date").asText().isEmpty()) {
                            d.put("date", firstDate.plusDays(dayNum - 1).format(DateTimeFormatter.ISO_DATE));
                        }
                        allDays.add(d);
                        dayNum++;
                    }
                }
            }
            // 补齐缺失的天数
            while (dayNum <= totalDays) {
                ObjectNode emptyDay = objectMapper.createObjectNode();
                emptyDay.put("day", dayNum);
                emptyDay.put("date", firstDate.plusDays(dayNum - 1).format(DateTimeFormatter.ISO_DATE));
                emptyDay.put("weather", "晴");
                emptyDay.put("temperature", "25°C");
                emptyDay.set("points", objectMapper.createArrayNode());
                ObjectNode hotel = objectMapper.createObjectNode();
                hotel.put("name", "");
                hotel.put("detail", "");
                hotel.put("price", 0);
                emptyDay.set("hotel", hotel);
                allDays.add(emptyDay);
                dayNum++;
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.set("days", allDays);
            return result.toString();
        } catch (Exception e) {
            log.error("合并分块 JSON 失败", e);
            return "{\"days\":[]}";
        }
    }

    private String mergeChunkShard4(List<String> chunkJsons, int totalDays) {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            for (String style : Arrays.asList("seasonal", "trendy", "classic")) {
                ArrayNode allDays = objectMapper.createArrayNode();
                int dayNum = 1;
                for (String chunkJson : chunkJsons) {
                    JsonNode chunk = objectMapper.readTree(chunkJson);
                    if (chunk.has(style)) {
                        JsonNode styleNode = chunk.get(style);
                        if (styleNode.has("days") && styleNode.get("days").isArray()) {
                            for (JsonNode day : styleNode.get("days")) {
                                ObjectNode d = (ObjectNode) day;
                                d.put("day", dayNum);
                                allDays.add(d);
                                dayNum++;
                            }
                        }
                    }
                }
                ObjectNode styleResult = objectMapper.createObjectNode();
                styleResult.set("days", allDays);
                result.set(style, styleResult);
            }
            return result.toString();
        } catch (Exception e) {
            log.error("合并 Shard4 失败", e);
            return "{}";
        }
    }

    // ==================== 数据库写入（兼容现有前端） ====================

    /**
     * 异步拉取景点图片，更新到 TripSpot 表
     */
    private void fetchImagesAsync(String tripId) {
        CompletableFuture.runAsync(() -> {
            try {
                List<TripDay> days = tripDayRepository.findByTripId(tripId);
                int count = 0;
                for (TripDay day : days) {
                    List<TripSpot> spots = tripSpotRepository.findByTripDayId(day.getId());
                    for (TripSpot spot : spots) {
                        if (spot.getName() != null && !spot.getName().isEmpty()) {
                            try {
                                String imageUrl = imageService.searchImageFromPixabay(spot.getName());
                                if (imageUrl != null && !imageUrl.isEmpty()) {
                                    spot.setImage(imageUrl);
                                    tripSpotRepository.updateById(spot);
                                    count++;
                                }
                            } catch (Exception e) {
                                log.debug("图片获取失败：{}", spot.getName());
                            }
                        }
                    }
                }
                log.info("📷 图片拉取完成：tripId={}, 成功={}", tripId, count);
            } catch (Exception e) {
                log.warn("图片拉取异常：tripId={}", tripId, e);
            }
        });
    }

    /**
     * 将路线A的数据写入 TripDay/TripSpot 表
     */
    private void saveRouteToTables(String tripId, CreateTripRequest request, List<JsonNode> daysArray, boolean fetchImages) {
        LocalDate startDate = parseDate(request.getStartDate());
        
        int targetDays = request.getDays();
        if (daysArray == null || daysArray.isEmpty()) {
            log.warn("路线A数据为空，创建默认空天数：tripId={}", tripId);
            for (int i = 1; i <= targetDays; i++) {
                TripDay day = new TripDay();
                day.setTripId(tripId);
                day.setDay(i);
                day.setDate(startDate.plusDays(i - 1).toString());
                tripDayRepository.insert(day);
            }
            return;
        }
        
        // 如果daysArray不足targetDays，补齐
        while (daysArray.size() < targetDays) {
            ObjectNode emptyDay = objectMapper.createObjectNode();
            emptyDay.put("day", daysArray.size() + 1);
            emptyDay.put("date", startDate.plusDays(daysArray.size()).format(DateTimeFormatter.ISO_DATE));
            emptyDay.put("weather", "晴");
            emptyDay.put("temperature", "25°C");
            emptyDay.set("points", objectMapper.createArrayNode());
            ObjectNode hotel = objectMapper.createObjectNode();
            hotel.put("name", "");
            hotel.put("detail", "");
            hotel.put("price", 0);
            emptyDay.set("hotel", hotel);
            daysArray.add(emptyDay);
        }
        
        for (int i = 0; i < daysArray.size(); i++) {
            JsonNode dayObj = daysArray.get(i);
            if (dayObj == null) continue;

            TripDay day = new TripDay();
            day.setTripId(tripId);
            day.setDay(dayObj.has("day") ? dayObj.get("day").asInt() : i + 1);
            day.setDate(dayObj.has("date") ? dayObj.get("date").asText() :
                    startDate.plusDays(i).format(DateTimeFormatter.ISO_DATE));
            day.setWeather(JsonUtils.getSafeText(dayObj, "weather", ""));
            day.setTemperature(JsonUtils.getSafeText(dayObj, "temperature", ""));
            day.setNotes(JsonUtils.getSafeText(dayObj, "notes", ""));

            JsonNode hotel = dayObj.get("hotel");
            if (hotel != null && !hotel.isNull()) {
                day.setHotelName(JsonUtils.getSafeText(hotel, "name", ""));
                day.setHotelDetail(JsonUtils.getSafeText(hotel, "detail", ""));
                day.setHotelPrice(hotel.has("price") ? hotel.get("price").asInt() : 0);
            }

            tripDayRepository.insert(day);

            JsonNode points = dayObj.get("points");
            if (points != null && points.isArray()) {
                List<TripSpot> spotList = new ArrayList<>();
                for (int j = 0; j < points.size(); j++) {
                    JsonNode pointObj = points.get(j);
                    TripSpot spot = new TripSpot();
                    spot.setTripDayId(day.getId());
                    spot.setName(JsonUtils.getSafeText(pointObj, "name", ""));
                    spot.setCategory(JsonUtils.getSafeText(pointObj, "category", ""));
                    spot.setAddress(JsonUtils.getSafeText(pointObj, "address", ""));
                    spot.setOrderNum(j + 1);
                    spot.setArrivalTime(JsonUtils.getSafeText(pointObj, "arrivalTime", ""));
                    spot.setDuration(pointObj.has("duration") ? String.valueOf(pointObj.get("duration").asInt()) : "0");
                    spot.setTemperature(JsonUtils.getSafeText(pointObj, "temperature", ""));
                    
                    // 调用 Pixabay API 获取景点图片
                    String spotName = JsonUtils.getSafeText(pointObj, "name", "");
                    if (fetchImages && !spotName.isEmpty()) {
                        try {
                            String imageUrl = imageService.searchImageFromPixabay(spotName);
                            spot.setImage(imageUrl);
                        } catch (Exception e) {
                            log.debug("图片获取失败：{}", spotName);
                        }
                    }
                    
                    spotList.add(spot);
                }
                // 批量插入景点，减少数据库调用
                if (!spotList.isEmpty()) {
                    for (TripSpot spot : spotList) {
                        tripSpotRepository.insert(spot);
                    }
                    // 解析并存储景点坐标（生成时一次性解析，后续地图页直接读取，无需实时调 API）
                    resolveSpotCoordinates(spotList, request.getDestination());
                }
            }
        }
    }

    // ==================== 景点坐标解析 ====================

    /**
     * 解析景点坐标并保存到 DB。
     * 策略：POI 全国搜 → geocode 全国搜 → 地址字段 geocode → 跳过
     * 生成时一次性解析，后续地图页直接读 DB 坐标，不再实时调 API。
     */
    private void resolveSpotCoordinates(List<TripSpot> spots, String destination) {
        if (spots == null || spots.isEmpty()) return;

        for (TripSpot spot : spots) {
            if (spot.getLatitude() != null && spot.getLongitude() != null
                    && spot.getLatitude() != 0 && spot.getLongitude() != 0) {
                continue; // 已有坐标，跳过
            }

            double lat = 0, lng = 0;
            String name = spot.getName();
            String address = spot.getAddress();

            // 1. POI 搜索（不带城市，全国搜）
            if (name != null && !name.isEmpty()) {
                try {
                    Map<String, Object> poiResult = mapService.searchPOI(name, "");
                    List<Map<String, Object>> pois = (List<Map<String, Object>>) poiResult.get("pois");
                    if (pois != null && !pois.isEmpty()) {
                        Map<String, Object> first = pois.get(0);
                        lat = ((Number) first.get("latitude")).doubleValue();
                        lng = ((Number) first.get("longitude")).doubleValue();
                    }
                } catch (Exception e) {
                    log.debug("POI搜索失败: {}", name);
                }
            }

            // 2. geocode（不带城市，全国搜）
            if (lat == 0 && lng == 0 && name != null && !name.isEmpty()) {
                try {
                    Map<String, Object> geoResult = mapService.geocode(name, "");
                    if (geoResult != null && geoResult.get("lat") != null && geoResult.get("lng") != null) {
                        lat = ((Number) geoResult.get("lat")).doubleValue();
                        lng = ((Number) geoResult.get("lng")).doubleValue();
                    }
                } catch (Exception e) {
                    log.debug("Geocode失败: {}", name);
                }
            }

            // 3. 用地址字段 geocode
            if (lat == 0 && lng == 0 && address != null && !address.isEmpty()) {
                try {
                    Map<String, Object> geoResult = mapService.geocode(address, "");
                    if (geoResult != null && geoResult.get("lat") != null && geoResult.get("lng") != null) {
                        lat = ((Number) geoResult.get("lat")).doubleValue();
                        lng = ((Number) geoResult.get("lng")).doubleValue();
                    }
                } catch (Exception e) {
                    log.debug("地址geocode失败: {}", address);
                }
            }

            // 保存坐标到 DB
            if (lat != 0 && lng != 0) {
                spot.setLatitude(lat);
                spot.setLongitude(lng);
                tripSpotRepository.updateById(spot);
                log.info("坐标已解析: {} → ({}, {})", name, lat, lng);
            } else {
                log.warn("坐标解析全部失败，跳过: name={}, address={}", name, address);
            }

            // 控制 API 调用频率，避免触发限流
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
    }

    // ==================== 徒步路线结构化存储 ====================

    /**
     * 将 3 条徒步路线写入 hiking_route + hiking_segment 表
     */
    private void saveHikingRoutes(String tripId, CreateTripRequest request,
                                  List<JsonNode> routeADays, List<JsonNode> routeBDays, List<JsonNode> routeCDays,
                                  JsonNode shard1, JsonNode shard2, JsonNode shard3, JsonNode shard4) {
        try {
            HikingProfileDTO hp = request.getHikingProfile();

            // 路线 A：风景休闲
            saveOneHikingRoute(tripId, request, routeADays, shard1, shard4, "scenic",
                    "🌿 风景休闲徒步", hp);

            // 路线 B：探险挑战
            saveOneHikingRoute(tripId, request, routeBDays, shard2, shard4, "adventure",
                    "🔥 探险挑战徒步", hp);

            // 路线 C：经典环线
            saveOneHikingRoute(tripId, request, routeCDays, shard3, shard4, "classic",
                    "⭐ 经典环线徒步", hp);

            log.info("🏔️ 徒步路线结构化存储完成：tripId={}", tripId);
        } catch (Exception e) {
            log.error("徒步路线结构化存储失败：tripId={}", tripId, e);
        }
    }

    /**
     * 保存单条徒步路线到 hiking_route + hiking_segment
     */
    private void saveOneHikingRoute(String tripId, CreateTripRequest request,
                                    List<JsonNode> days, JsonNode shardData, JsonNode shard4,
                                    String styleKey, String routeName, HikingProfileDTO hp) {
        HikingRoute route = new HikingRoute();
        route.setTripPlanId(tripId);
        route.setRouteName(routeName);
        route.setSource("ai");
        route.setIsPublic(0);
        route.setViewCount(0);
        route.setLikeCount(0);
        route.setCreatorId("");

        // 从 AI 返回的 JSON 中提取徒步核心数据
        if (shardData != null) {
            route.setTotalDistance(parseBigDecimal(shardData, "totalDistance"));
            route.setTotalAscent(parseInteger(shardData, "totalAscent"));
            route.setTotalDescent(parseInteger(shardData, "totalDescent"));
            route.setWalkTime(JsonUtils.getSafeText(shardData, "walkTime", ""));
            route.setTotalTime(JsonUtils.getSafeText(shardData, "totalTime", ""));
            route.setDifficulty(parseInteger(shardData, "difficulty"));
            route.setRouteType(JsonUtils.getSafeText(shardData, "routeType", ""));

            // 路况占比
            JsonNode roadRatio = shardData.get("roadRatio");
            if (roadRatio != null && !roadRatio.isNull()) {
                route.setRoadRatio(roadRatio.toString());
            }

            // 安全指南
            JsonNode safetyGuide = shardData.get("safetyGuide");
            if (safetyGuide != null && !safetyGuide.isNull()) {
                route.setSafetyJson(safetyGuide.toString());
            }

            // 装备建议
            JsonNode gearAdvice = shardData.get("gearAdvice");
            if (gearAdvice != null && !gearAdvice.isNull()) {
                route.setGearJson(gearAdvice.toString());
            }

            // 补给信息
            JsonNode supplyInfo = shardData.get("supplyInfo");
            if (supplyInfo != null && !supplyInfo.isNull()) {
                route.setSupplyJson(supplyInfo.toString());
            }
        }

        // 从 hikingProfile 填充推断字段
        if (hp != null) {
            if (hp.getDifficulty() != null) {
                route.setDifficulty(hp.getDifficulty());
            }
            if (hp.getRouteTypes() != null && !hp.getRouteTypes().isEmpty()) {
                route.setRouteType(hp.getRouteTypes().get(0)); // 取第一个类型
            }
            if (Boolean.TRUE.equals(hp.getNeedCampsite())) {
                route.setHasCampsite(1);
            }
            if ("parent-child".equals(hp.getGroupType())) {
                route.setIsFamilyFriendly(1);
            }
        }

        // 从 shard4 提取对应风格的装备建议
        if (shard4 != null) {
            JsonNode styleGear = shard4.get(styleKey);
            if (styleGear != null && !styleGear.isNull()) {
                // 如果主数据没有装备建议，用 shard4 补充
                if (route.getGearJson() == null || route.getGearJson().isEmpty()) {
                    route.setGearJson(styleGear.toString());
                }
            }
        }

        hikingRouteRepository.insert(route);
        String routeId = route.getId();

        // 保存路段详情
        int segOrder = 0;
        for (int i = 0; i < days.size(); i++) {
            JsonNode dayObj = days.get(i);
            if (dayObj == null) continue;

            int dayNum = dayObj.has("day") ? dayObj.get("day").asInt() : i + 1;

            // 从 days 数组中提取 segments
            JsonNode segments = dayObj.get("segments");
            if (segments != null && segments.isArray()) {
                for (JsonNode seg : segments) {
                    HikingSegment entity = new HikingSegment();
                    entity.setHikingRouteId(routeId);
                    entity.setDayNum(dayNum);
                    entity.setOrderNum(segOrder++);
                    entity.setName(JsonUtils.getSafeText(seg, "name", ""));
                    entity.setDistance(parseBigDecimal(seg, "distance"));
                    entity.setAscent(parseInteger(seg, "ascent"));
                    entity.setDescent(parseInteger(seg, "descent"));
                    entity.setRoadType(JsonUtils.getSafeText(seg, "roadType", ""));
                    entity.setSlope(JsonUtils.getSafeText(seg, "slope", ""));
                    entity.setHighlights(JsonUtils.getSafeText(seg, "highlights", ""));
                    entity.setRestPoint(JsonUtils.getSafeText(seg, "restPoint", ""));
                    entity.setRiskTip(JsonUtils.getSafeText(seg, "riskTip", ""));
                    entity.setSignalStrength(JsonUtils.getSafeText(seg, "signal", ""));
                    hikingSegmentRepository.insert(entity);
                }
            }

            // 如果没有 segments 数据，从 points 自动构建简略路段
            if ((segments == null || !segments.isArray() || segments.size() == 0)
                    && dayObj.has("points") && dayObj.get("points").isArray()) {
                JsonNode points = dayObj.get("points");
                for (int j = 0; j < points.size() - 1; j++) {
                    JsonNode from = points.get(j);
                    JsonNode to = points.get(j + 1);
                    HikingSegment entity = new HikingSegment();
                    entity.setHikingRouteId(routeId);
                    entity.setDayNum(dayNum);
                    entity.setOrderNum(segOrder++);
                    entity.setName(JsonUtils.getSafeText(from, "name", "") + " → " + JsonUtils.getSafeText(to, "name", ""));
                    entity.setRoadType(JsonUtils.getSafeText(from, "category", ""));
                    hikingSegmentRepository.insert(entity);
                }
            }
        }
    }

    /**
     * 从 JsonNode 安全解析 BigDecimal
     */
    private java.math.BigDecimal parseBigDecimal(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        try {
            String val = node.get(field).asText();
            // 去掉单位（如 "12.5km" → "12.5"）
            val = val.replaceAll("[^0-9.]", "");
            return val.isEmpty() ? null : new java.math.BigDecimal(val);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JsonNode 安全解析 Integer
     */
    private Integer parseInteger(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        try {
            return node.get(field).asInt();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成行李清单并存储到数据库
     */
    private void generateAndSavePackingList(String tripId, CreateTripRequest request) {
        try {
            log.info("🎒 开始生成行李清单：tripId={}", tripId);
            
            // 调用 AI 生成行李清单
            String packingListJson = aiService.generatePackingList(
                    request.getDestination(), 
                    request.getDays(), 
                    request.getTravelMode(),
                    "");
            
            if (packingListJson != null && !packingListJson.isEmpty()) {
                // 存储到数据库
                TripPlan tripPlan = tripPlanRepository.selectById(tripId);
                if (tripPlan != null) {
                    tripPlan.setPackingListJson(packingListJson);
                    tripPlanRepository.updateById(tripPlan);
                    log.info("🎒 行李清单已存储：tripId={}, length={}", tripId, packingListJson.length());
                }
            } else {
                log.warn("🎒 行李清单生成失败或为空：tripId={}", tripId);
            }
        } catch (Exception e) {
            log.warn("🎒 行李清单生成异常：tripId={}, error={}", tripId, e.getMessage());
        }
    }

    /**
     * 创建默认空行程（全失败兜底）
     */
    private void createDefaultTrip(String tripId, CreateTripRequest request) {
        LocalDate startDate = parseDate(request.getStartDate());

        TripPlan tripPlan = tripPlanRepository.selectById(tripId);
        if (tripPlan != null) {
            tripPlan.setName(request.getDestination() + " " + request.getDays() + "日游");
            tripPlan.setDescription("AI智能生成的行程计划");
            tripPlan.setPreferences(request.getPreferences() != null ?
                    String.join(",", request.getPreferences()) : "");
            tripPlan.setMustVisitPlaces(request.getMustVisitPlaces() != null ?
                    String.join(",", request.getMustVisitPlaces()) : "");
            tripPlan.setStatus("DRAFT");
            tripPlanRepository.updateById(tripPlan);
        }

        for (int i = 1; i <= request.getDays(); i++) {
            TripDay day = new TripDay();
            day.setTripId(tripId);
            day.setDay(i);
            day.setDate(startDate.plusDays(i - 1).toString());
            tripDayRepository.insert(day);
        }

        log.info("创建默认空行程完成：tripId={}", tripId);
    }

    /**
     * 从日期字符串提取月份
     */
    private String extractMonth(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return String.valueOf(LocalDate.now().getMonthValue());
        }
        try {
            LocalDate date = parseDate(dateStr);
            return String.valueOf(date.getMonthValue());
        } catch (Exception e) {
            return String.valueOf(LocalDate.now().getMonthValue());
        }
    }

    /**
     * 解析日期字符串，兼容带时间和不带时间的格式
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return LocalDate.now();
        }
        try {
            // 先尝试去掉时间部分
            String dateOnly = dateStr.split(" ")[0];
            return LocalDate.parse(dateOnly, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            log.warn("日期解析失败，使用默认日期：{}", dateStr, e);
            return LocalDate.now();
        }
    }

    // ==================== 天气获取 ====================

    private String fetchWeather(String destination, String startDateStr, int days) {
        try {
            // 用腾讯地图逆地理编码获取城市坐标
            Map<String, Object> geocodeResult = mapService.geocode(destination, destination.substring(0, Math.min(2, destination.length())));
            if (geocodeResult == null || !geocodeResult.containsKey("location")) {
                log.warn("获取{}坐标失败，跳过天气", destination);
                return "";
            }
            Map<String, Object> location = (Map<String, Object>) geocodeResult.get("location");
            double lat = ((Number) location.get("lat")).doubleValue();
            double lng = ((Number) location.get("lng")).doubleValue();

            Map<String, Object> weatherResult = mapService.getWeather(lat, lng);
            if (weatherResult == null) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("\n\n【真实天气预报，路线规划时请务必参考】\n");
            List<Map<String, Object>> forecasts = (List<Map<String, Object>>) weatherResult.get("forecast_24h");
            if (forecasts != null && !forecasts.isEmpty()) {
                for (int i = 0; i < Math.min(forecasts.size(), days); i++) {
                    Map<String, Object> day = forecasts.get(i);
                    sb.append("第").append(i + 1).append("天(")
                      .append(day.get("time")).append(")：")
                      .append(day.get("day_weather")).append("，")
                      .append(day.get("day_wind_direction")).append("风")
                      .append(day.get("day_wind_scale")).append("级，")
                      .append("气温").append(day.get("low_degree")).append("~").append(day.get("high_degree")).append("°C")
                      .append("。\n");
                }
            }
            log.info("天气预报已注入：destination={}, days={}, length={}", destination, days, sb.length());
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取天气失败：{}", e.getMessage());
            return "";
        }
    }

    // ==================== 单天重新生成 ====================

    public void regenerateDay(String tripId, int dayNum) {
        TripPlan tripPlan = tripPlanRepository.selectById(tripId);
        if (tripPlan == null || tripPlan.getRoutesJson() == null) return;

        try {
            ObjectNode routesRoot = (ObjectNode) objectMapper.readTree(tripPlan.getRoutesJson());
            ArrayNode routes = (ArrayNode) routesRoot.get("routes");
            if (routes == null || routes.size() == 0) return;

            // 取第一条路线（路线A）
            ObjectNode routeA = (ObjectNode) routes.get(0);
            ArrayNode days = (ArrayNode) routeA.get("days");

            String destination = tripPlan.getDestination();
            String budget = tripPlan.getBudget() != null ? tripPlan.getBudget() : "medium";
            String startDate = tripPlan.getStartDate();
            String travelMode = tripPlan.getTravelMode() != null ? tripPlan.getTravelMode() : "drive";
            String startPoint = tripPlan.getStartPoint();
            String endPoint = tripPlan.getEndPoint();
            boolean isFirstDay = (dayNum == 1);
            boolean isLastDay = (days != null && dayNum == days.size());

            // 构建智能 Prompt
            StringBuilder prompt = new StringBuilder();
            prompt.append("目的地：").append(destination).append("。出行方式：")
                  .append(getTravelModeText(travelMode)).append("。");

            // 第一天需要从起点出发
            if (isFirstDay && startPoint != null && !startPoint.isEmpty()) {
                if (!startPoint.equals(destination)) {
                    prompt.append("⚠️ 这是从").append(startPoint).append("出发的第一天，如果是自驾则需要规划沿途路线。");
                } else {
                    prompt.append("这是").append(destination).append("行程的第一天。");
                }
                if (days != null && days.size() > 1) {
                    prompt.append("第二天将去：").append(days.get(1).toString()).append("。");
                }
            } else if (isLastDay && endPoint != null && !endPoint.isEmpty() && !endPoint.equals(destination)) {
                // 最后一天需要返回终点
                prompt.append("⚠️ 最后一天需要前往").append(endPoint).append("，规划返程路线。");
                if (dayNum > 1 && days != null) {
                    prompt.append("前一天的行程：").append(days.get(dayNum - 2).toString()).append("。");
                }
            } else {
                // 中间天：参考前后天保持连贯
                if (dayNum > 1 && days != null && dayNum - 2 < days.size()) {
                    prompt.append("前一天的行程：").append(days.get(dayNum - 2).toString()).append("。");
                }
                if (days != null && dayNum < days.size()) {
                    prompt.append("后一天的行程：").append(days.get(dayNum).toString()).append("。");
                }
            }

            ShardResult shards = aiService.generateShards(destination, 1, budget, "", "",
                    startDate, startDate,
                    isFirstDay ? startPoint : null,
                    isLastDay ? endPoint : null,
                    travelMode, prompt.toString());

            JsonNode newDayJson = parseJsonSafe(shards.getShard1());
            if (newDayJson == null || !newDayJson.has("days")) return;

            JsonNode newDay = newDayJson.get("days").get(0);
            if (newDay == null) return;

            // 替换第 N 天
            if (days != null && dayNum - 1 < days.size()) {
                days.set(dayNum - 1, newDay);
            }

            // 更新 DB
            tripPlan.setRoutesJson(objectMapper.writeValueAsString(routesRoot));
            tripPlanRepository.updateById(tripPlan);

            // 重新写入 TripDay/TripSpot
            deleteExistingDays(tripId);
            List<JsonNode> routeADays = new ArrayList<>();
            for (int i = 0; i < days.size(); i++) {
                routeADays.add(days.get(i));
            }
            CreateTripRequest fakeReq = new CreateTripRequest();
            fakeReq.setStartDate(startDate);
            fakeReq.setDays(days.size());
            fakeReq.setDestination(destination);
            saveRouteToTables(tripId, fakeReq, routeADays, false);

            List<TripDay> savedDays = tripDayRepository.findByTripId(tripId);
            log.info("单天重生成完成：tripId={}, dayNum={}, 天数={}", tripId, dayNum, savedDays.size());

        } catch (Exception e) {
            log.error("单天重生成失败：tripId={}, dayNum={}", tripId, dayNum, e);
        }
    }

    private void deleteExistingDays(String tripId) {
        List<TripDay> days = tripDayRepository.findByTripId(tripId);
        for (TripDay day : days) {
            tripSpotRepository.deleteByTripDayId(day.getId());
            tripDayRepository.deleteById(day.getId());
        }
    }

    private String getTravelModeText(String mode) {
        if (mode == null) return "自驾";
        switch (mode) {
            case "hiking": return "徒步";
            case "transit": return "公共交通";
            case "bike": return "骑行";
            default: return "自驾";
        }
    }
}
