package com.ai.travel.service.impl;

import com.ai.travel.dto.HikingProfileDTO;
import com.ai.travel.dto.ShardResult;
import com.ai.travel.service.AIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ShardResult generateShards(String destination, int days, String budget,
                                      String preferences, String mustVisitPlaces,
                                      String startDate, String endDate,
                                      String startPoint, String endPoint, String travelMode,
                                      String hotTravelData) {
        // 委托给新方法，hikingProfile 为 null
        return generateShards(destination, days, budget, preferences, mustVisitPlaces,
                startDate, endDate, startPoint, endPoint, travelMode, hotTravelData, null);
    }

    /**
     * 完整版 4 分片并行生成（含徒步专属参数）
     */
    public ShardResult generateShards(String destination, int days, String budget,
                                      String preferences, String mustVisitPlaces,
                                      String startDate, String endDate,
                                      String startPoint, String endPoint, String travelMode,
                                      String hotTravelData, HikingProfileDTO hikingProfile) {

        boolean isHiking = "hiking".equals(travelMode);
        log.info("🚀 启动 4 分片并行 AI 生成：destination={}, days={}, isHiking={}", destination, days, isHiking);

        try {
            // 公共参数拼接
            String commonParams = buildCommonParams(destination, days, budget, preferences,
                    mustVisitPlaces, startDate, endDate, startPoint, endPoint, travelMode);

            // 根据天数动态计算 max_tokens（天数越多输出越长，防止截断）
            // 每 1 天大约需要 1500-2000 tokens 的 JSON 输出，乘以 1.5 保险系数
            // DeepSeek chat 模型最大输出 32000 tokens
            final int maxTokensRoute = Math.min(32000, 8000 + days * 1800);
            final int maxTokensShard4 = Math.min(24000, 6000 + days * 1200);
            log.info("动态 max_tokens: routeShards={}, shard4={}, days={}", maxTokensRoute, maxTokensShard4, days);

            // 徒步专属参数（必须 final，用于 lambda）
            final String hikingParams = (isHiking && hikingProfile != null)
                    ? buildHikingParams(hikingProfile)
                    : "";

            // 热点数据注入（必须 final，用于 lambda）
            final String hotDataSection = (hotTravelData != null && !hotTravelData.isEmpty())
                    ? "\n\n【当月热点参考，请务必参考以下信息规划路线】\n" + hotTravelData + "\n"
                    : "";

            // 徒步模式使用专属 Prompt，普通模式使用原 Prompt
            if (isHiking) {
                // 徒步模式：3 条不同风格的徒步路线 + 1 条装备/安全指南
                CompletableFuture<String> shard1Future = CompletableFuture.supplyAsync(() ->
                        callAI(buildHikingShard1Prompt(destination, commonParams, hikingParams, hotDataSection), maxTokensRoute, 0.7)
                );
                CompletableFuture<String> shard2Future = CompletableFuture.supplyAsync(() ->
                        callAI(buildHikingShard2Prompt(destination, commonParams, hikingParams, hotDataSection), maxTokensRoute, 0.7)
                );
                CompletableFuture<String> shard3Future = CompletableFuture.supplyAsync(() ->
                        callAI(buildHikingShard3Prompt(destination, commonParams, hikingParams, hotDataSection), maxTokensRoute, 0.7)
                );
                CompletableFuture<String> shard4Future = CompletableFuture.supplyAsync(() ->
                        callAI(buildHikingShard4Prompt(destination, commonParams, hikingParams, hotDataSection), maxTokensShard4, 0.5)
                );

                CompletableFuture.allOf(shard1Future, shard2Future, shard3Future, shard4Future)
                        .get(120, TimeUnit.SECONDS);

                return ShardResult.builder()
                        .shard1(shard1Future.get())
                        .shard2(shard2Future.get())
                        .shard3(shard3Future.get())
                        .shard4(shard4Future.get())
                        .build();
            }

            // 普通模式：原有 4 分片逻辑
            CompletableFuture<String> shard1Future = CompletableFuture.supplyAsync(() ->
                    callAI(buildShard1Prompt(destination, days, commonParams, hotDataSection), maxTokensRoute, 0.7)
            );
            CompletableFuture<String> shard2Future = CompletableFuture.supplyAsync(() ->
                    callAI(buildShard2Prompt(destination, days, commonParams, hotDataSection), maxTokensRoute, 0.7)
            );
            CompletableFuture<String> shard3Future = CompletableFuture.supplyAsync(() ->
                    callAI(buildShard3Prompt(destination, days, commonParams, hotDataSection), maxTokensRoute, 0.7)
            );
            CompletableFuture<String> shard4Future = CompletableFuture.supplyAsync(() ->
                    callAI(buildShard4Prompt(destination, days, commonParams, hotDataSection), maxTokensShard4, 0.5)
            );

            // 等待所有完成
            CompletableFuture.allOf(shard1Future, shard2Future, shard3Future, shard4Future)
                    .get(120, TimeUnit.SECONDS);

            String s1 = shard1Future.get();
            String s2 = shard2Future.get();
            String s3 = shard3Future.get();
            String s4 = shard4Future.get();

            log.info("✅ 4 分片全部完成：s1_len={}, s2_len={}, s3_len={}, s4_len={}",
                    s1.length(), s2.length(), s3.length(), s4.length());

            return ShardResult.builder()
                    .shard1(s1)
                    .shard2(s2)
                    .shard3(s3)
                    .shard4(s4)
                    .build();

        } catch (Exception e) {
            log.error("❌ 4 分片并行生成失败", e);
            // 返回默认空数据
            String emptyDays = generateEmptyDaysJson(destination, days, startDate);
            String emptyShard4 = generateEmptyShard4Json(destination, days);
            return ShardResult.builder()
                    .shard1(emptyDays)
                    .shard2(emptyDays)
                    .shard3(emptyDays)
                    .shard4(emptyShard4)
                    .build();
        }
    }

    // ==================== Prompt 构建 ====================

    /**
     * 构建公共参数文本
     */
    private String buildCommonParams(String destination, int days, String budget,
                                     String preferences, String mustVisitPlaces,
                                     String startDate, String endDate,
                                     String startPoint, String endPoint, String travelMode) {
        StringBuilder p = new StringBuilder();
        p.append("目的地：").append(destination).append("，").append(days).append("日游。");
        // 预算：自定义金额时给 AI 明确的预算约束指令
        String budgetText = getBudgetText(budget);
        p.append("预算：").append(budgetText).append("。");
        if (budget != null && budget.matches(".*\\d+.*")) {
            String amount = budget.replaceAll("[^\\d]", "");
            p.append("【预算约束】总预算").append(amount).append("元，");
            p.append("请严格按照此预算规划：酒店价格（hotel.price）需符合预算档次，");
            p.append("每日餐饮推荐也需控制在预算范围内，");
            p.append("总花费（住宿+餐饮+门票+交通）不能超过").append(amount).append("元！");
        }
        p.append("偏好：").append(getPreferenceText(preferences)).append("。");
        p.append("出行方式：").append(getTravelModeText(travelMode)).append("。");
        p.append("日期：").append(startDate).append(" 至 ").append(endDate).append("。");
        if (mustVisitPlaces != null && !mustVisitPlaces.isEmpty()) {
            p.append("必去景点：").append(mustVisitPlaces).append("。");
        }
        if (startPoint != null && !startPoint.isEmpty()) {
            p.append("出发起点：").append(startPoint).append("。");
            boolean hasReturn = endPoint != null && !endPoint.isEmpty()
                    && !endPoint.equals(destination) && endPoint.equals(startPoint);

            if (!startPoint.equals(destination)) {
                if (hasReturn) {
                    // 往返路线：起点=终点，如成都→阿坝→成都
                    p.append("⚠️ 这是一条往返路线：从").append(startPoint).append("出发去").append(destination);
                    p.append("，").append(days).append("天后返回").append(startPoint).append("。");
                    p.append("最后一天必须规划从").append(destination).append("返回").append(startPoint).append("的路线！");
                } else {
                    // 单程路线：成都→厦门，不同终点
                    p.append("⚠️ 一次从").append(startPoint).append("到").append(destination);
                    p.append("的单程「").append(getTravelModeText(travelMode)).append("旅行」，");
                    p.append("以").append(destination).append("为终点，不要规划返程！");
                }
                if ("drive".equals(travelMode)) {
                    p.append("请规划沿途自驾路线，每天安排沿途城市/景点，到达").append(destination).append("后继续游玩。");
                } else {
                    p.append("第一天抵达").append(destination).append("后开始游览。");
                }
                p.append("。");
            } else if ("drive".equals(travelMode)) {
                p.append("⚠️ ").append(destination).append("及周边自驾游，每天安排自驾前往的景点。");
                p.append("。");
            }
        }
        if (endPoint != null && !endPoint.isEmpty() && !endPoint.equals(destination)
                && !endPoint.equals(startPoint)) {
            // 只有终点≠起点时才单独加返程提示（否则已在上面往返逻辑中处理）
            p.append("返回终点：").append(endPoint).append("。最后一天从").append(destination);
            p.append("返回").append(endPoint).append("，需规划返程路线。");
        }
        return p.toString();
    }

    /**
     * 分片 1：当月时令定制版行程
     */
    private String buildShard1Prompt(String destination, int days, String common, String hotData) {
        return common + hotData +
                "\n请为" + destination + "规划一套「当月时令定制版」" + days + "日游路线。" +
                "\n要求：突出当月应季特色、时令活动、当季美食，结合当月气候特点。" +
                "\n\n每天安排 3-5 个景点，出发时间 8-9 点，标注到达时间(HH:MM)和游玩时长(duration,小时)。" +
                "\n同时推荐当天适合的酒店（含名称、简介、价格）。" +
                "\n\n⚠️ 必须输出正好 " + days + " 天的完整行程（第1天到第" + days + "天），一天都不能少！" +
                "\n\n请按以下 JSON 格式输出完整的 " + days + " 天行程（只输出JSON，不要其他文字）：" +
                "\n{\"days\":[" +
                "\n  {\"day\":1,\"date\":\"2026-06-15\",\"weather\":\"晴\",\"temperature\":\"28°C\"," +
                "\n   \"points\":[{\"name\":\"景点名\",\"address\":\"地址\",\"arrivalTime\":\"09:00\",\"duration\":2,\"category\":\"景点类型\"}]," +
                "\n   \"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300}}," +
                "\n  ...," +
                "\n  {\"day\":" + days + ",\"date\":\"2026-06-" + (14 + days) + "\",\"weather\":\"晴\",\"temperature\":\"28°C\"," +
                "\n   \"points\":[{\"name\":\"景点名\",\"address\":\"地址\",\"arrivalTime\":\"09:00\",\"duration\":2,\"category\":\"景点类型\"}]," +
                "\n   \"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300}}" +
                "\n]}";
    }

    /**
     * 分片 2：当下网红爆款版行程
     */
    private String buildShard2Prompt(String destination, int days, String common, String hotData) {
        return common + hotData +
                "\n请为" + destination + "规划一套「当下网红爆款版」" + days + "日游路线。" +
                "\n要求：包含社交媒体上最热门的打卡景点、网红店铺、拍照圣地，路线要适合发朋友圈/小红书。" +
                "\n\n每天安排 3-5 个景点，出发时间 8-9 点，标注到达时间(HH:MM)和游玩时长(duration,小时)。" +
                "\n同时推荐当天适合的酒店（含名称、简介、价格）。" +
                "\n\n⚠️ 必须输出正好 " + days + " 天的完整行程（第1天到第" + days + "天），一天都不能少！" +
                "\n\n请按以下 JSON 格式输出完整的 " + days + " 天行程（只输出JSON，不要其他文字）：" +
                "\n{\"days\":[" +
                "\n  {\"day\":1,\"date\":\"2026-06-15\",\"weather\":\"晴\",\"temperature\":\"28°C\"," +
                "\n   \"points\":[{\"name\":\"景点名\",\"address\":\"地址\",\"arrivalTime\":\"09:00\",\"duration\":2,\"category\":\"景点类型\"}]," +
                "\n   \"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300}}," +
                "\n  ...," +
                "\n  {\"day\":" + days + ",\"date\":\"2026-06-" + (14 + days) + "\",\"weather\":\"晴\",\"temperature\":\"28°C\"," +
                "\n   \"points\":[{\"name\":\"景点名\",\"address\":\"地址\",\"arrivalTime\":\"09:00\",\"duration\":2,\"category\":\"景点类型\"}]," +
                "\n   \"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300}}" +
                "\n]}";
    }

    /**
     * 分片 3：经典稳妥路线行程
     */
    private String buildShard3Prompt(String destination, int days, String common, String hotData) {
        return common + hotData +
                "\n请为" + destination + "规划一套「经典稳妥路线」" + days + "日游路线。" +
                "\n要求：包含该目的地最经典、最成熟的必去景点，路线安排合理、交通便利，适合大多数游客。" +
                "\n\n每天安排 3-5 个景点，出发时间 8-9 点，标注到达时间(HH:MM)和游玩时长(duration,小时)。" +
                "\n同时推荐当天适合的酒店（含名称、简介、价格）。" +
                "\n\n⚠️ 必须输出正好 " + days + " 天的完整行程（第1天到第" + days + "天），一天都不能少！" +
                "\n\n请按以下 JSON 格式输出完整的 " + days + " 天行程（只输出JSON，不要其他文字）：" +
                "\n{\"days\":[" +
                "\n  {\"day\":1,\"date\":\"2026-06-15\",\"weather\":\"晴\",\"temperature\":\"28°C\"," +
                "\n   \"points\":[{\"name\":\"景点名\",\"address\":\"地址\",\"arrivalTime\":\"09:00\",\"duration\":2,\"category\":\"景点类型\"}]," +
                "\n   \"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300}}," +
                "\n  ...," +
                "\n  {\"day\":" + days + ",\"date\":\"2026-06-" + (14 + days) + "\",\"weather\":\"晴\",\"temperature\":\"28°C\"," +
                "\n   \"points\":[{\"name\":\"景点名\",\"address\":\"地址\",\"arrivalTime\":\"09:00\",\"duration\":2,\"category\":\"景点类型\"}]," +
                "\n   \"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300}}" +
                "\n]}";
    }

    /**
     * 分片 4：食宿推荐（3 套风格，按天匹配）
     */
    private String buildShard4Prompt(String destination, int days, String common, String hotData) {
        return common + hotData +
                "\n请为" + destination + days + "日游推荐住宿和餐饮方案。" +
                "\n需要提供 3 套风格（对应 3 条不同路线的食宿）：" +
                "\n① seasonal：适合「时令定制版」路线的食宿（注重当地特色、应季美食）" +
                "\n② trendy：适合「网红爆款版」路线的食宿（网红酒店、打卡餐厅）" +
                "\n③ classic：适合「经典稳妥版」路线的食宿（品质可靠、性价比高）" +
                "\n\n每套风格需要按天输出 " + days + " 天的酒店推荐和当日美食推荐。" +
                "\n\n请按以下 JSON 格式输出（只输出JSON，不要其他文字）：" +
                "\n{\"seasonal\":{\"days\":[" +
                "\n  {\"day\":1,\"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300},\"food\":\"当日美食推荐\"}]}," +
                "\n \"trendy\":{\"days\":[" +
                "\n  {\"day\":1,\"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300},\"food\":\"当日美食推荐\"}]}," +
                "\n \"classic\":{\"days\":[" +
                "\n  {\"day\":1,\"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300},\"food\":\"当日美食推荐\"}]}" +
                "\n}";
    }

    // ==================== 徒步专属 Prompt 构建 ====================

    /**
     * 构建徒步专属参数文本
     */
    private String buildHikingParams(HikingProfileDTO p) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【徒步专属参数】");

        // 出行人数
        if (p.getGroupType() != null) {
            sb.append("\n出行人数：");
            switch (p.getGroupType()) {
                case "solo": sb.append("单人徒步"); break;
                case "parent-child": sb.append("亲子徒步（有小孩同行，降低难度、增加休息点、缩短路段）"); break;
                case "team": sb.append("组队徒步（多人出行，路线宽度适中）"); break;
                default: sb.append(p.getGroupType());
            }
        }

        // 期望里程
        if (p.getDistanceRange() != null) {
            sb.append("\n期望里程：").append(p.getDistanceRange());
        }

        // 可用时长
        if (p.getDurationType() != null) {
            sb.append("\n可用时长：");
            switch (p.getDurationType()) {
                case "half-day": sb.append("半日（约4-5小时）"); break;
                case "full-day": sb.append("1日（约8-10小时）"); break;
                case "multi-day": sb.append("2日及以上（需安排露营地或沿途住宿）"); break;
                default: sb.append(p.getDurationType());
            }
        }

        // 路线类型
        if (p.getRouteTypes() != null && !p.getRouteTypes().isEmpty()) {
            sb.append("\n路线类型：").append(String.join("、", p.getRouteTypes()));
        }

        // 难度
        if (p.getDifficulty() != null) {
            String[] diffLabels = {"", "轻松（适合初次徒步，路面平缓）", "休闲（有一定坡度，休闲锻炼）",
                    "中等（中等爬升，需要基本体力）", "挑战（较大爬升，需要徒步经验）", "专业（专业路线，需装备和经验）"};
            sb.append("\n难度偏好：").append(p.getDifficulty()).append("星 - ")
                    .append(p.getDifficulty() <= 5 && p.getDifficulty() >= 1 ? diffLabels[p.getDifficulty()] : "");
        }

        // 景观偏好
        if (p.getSceneryPrefs() != null && !p.getSceneryPrefs().isEmpty()) {
            sb.append("\n景观偏好：").append(String.join("、", p.getSceneryPrefs()));
        }

        // 路况要求
        if (p.getRoadCondition() != null && !p.getRoadCondition().isEmpty()) {
            sb.append("\n路况要求：").append(p.getRoadCondition());
        }

        // 体力限制
        if (p.getPhysicalLimit() != null && !p.getPhysicalLimit().isEmpty()) {
            sb.append("\n体力限制：").append(String.join("、", p.getPhysicalLimit()))
              .append("（请自动降低爬升、缩短路段距离、增加休息点）");
        }

        // 交通限制
        if (p.getTrafficLimit() != null && !p.getTrafficLimit().isEmpty()) {
            sb.append("\n交通限制：").append(p.getTrafficLimit());
        }

        // 硬性规避
        if (p.getAvoidItems() != null && !p.getAvoidItems().isEmpty()) {
            sb.append("\n硬性规避：").append(String.join("、", p.getAvoidItems()));
        }

        // 露营需求
        if (Boolean.TRUE.equals(p.getNeedCampsite())) {
            sb.append("\n露营需求：需要沿途露营地（优先规划长线 + 营地位置）");
        }

        // 附加需求
        if (p.getAdvancedNeeds() != null && !p.getAdvancedNeeds().isEmpty()) {
            sb.append("\n附加需求：").append(String.join("、", p.getAdvancedNeeds()));
        }

        return sb.toString();
    }

    /**
     * 徒步分片1：风景休闲徒步路线
     */
    private String buildHikingShard1Prompt(String destination, String common, String hikingParams, String hotData) {
        return "你是专业徒步路线规划师。只输出JSON，无其他内容。\n\n" +
                common + hikingParams + hotData +
                "\n\n请为" + destination + "规划一条「风景休闲徒步路线」。" +
                "\n要求：以自然风光为主线，沿途安排最佳观景点、休息补给点，路线平缓舒适。" +
                "\n将整条路线按路段拆解，每段标注距离、路况、坡度、亮点景观、休息点、风险提示。" +
                "\n\n请按以下 JSON 格式输出（只输出JSON，不要其他文字）：" +
                "\n{\"routeName\":\"路线名称\",\"totalDistance\":\"12.5km\",\"totalAscent\":\"580m\",\"totalDescent\":\"560m\"," +
                "\"walkTime\":\"4h30m\",\"totalTime\":\"6h（含休息）\"," +
                "\"roadRatio\":{\"台阶路\":\"20%\",\"土路\":\"50%\",\"栈道\":\"20%\",\"野路\":\"10%\"}," +
                "\"difficulty\":3," +
                "\"days\":[{" +
                "\n  \"day\":1,\"date\":\"2026-06-15\",\"weather\":\"晴\",\"temperature\":\"25°C\"," +
                "\n  \"segments\":[" +
                "\n    {\"name\":\"起点→观景台\",\"distance\":\"2.5km\",\"ascent\":\"180m\",\"descent\":\"50m\"," +
                "\n     \"roadType\":\"土路+台阶\",\"slope\":\"缓坡\",\"highlights\":\"竹林、溪流\"," +
                "\n     \"restPoint\":\"1.5km处有凉亭\",\"riskTip\":\"雨后路滑注意脚下\"," +
                "\n     \"signal\":\"信号良好\"}]," +
                "\n  \"points\":[{\"name\":\"点位名\",\"address\":\"地址\",\"arrivalTime\":\"09:00\"," +
                "\n     \"duration\":1.5,\"category\":\"观景台\",\"isRestPoint\":true,\"hasWater\":false," +
                "\n     \"hasToilet\":true,\"signalStrength\":\"良好\"}]," +
                "\n  \"hotel\":{\"name\":\"民宿名\",\"detail\":\"简介\",\"price\":200}}]," +
                "\n\"safetyGuide\":{\"dangerSections\":[\"K3-K5碎石陡坡段\"],\"precautions\":\"穿防滑鞋，带登山杖\"," +
                "\n  \"signalMap\":[{\"section\":\"起点-3km\",\"signal\":\"良好\"},{\"section\":\"3km-7km\",\"signal\":\"弱\"}]," +
                "\n  \"emergencyInfo\":{\"nearestVillage\":\"XX村 3km\",\"emergencyPhone\":\"119\",\"evacRoute\":\"7km处可沿溪下撤到XX村\"}}," +
                "\n\"gearAdvice\":{\"clothing\":[\"速干衣\",\"防滑徒步鞋\"],\"supplies\":[\"1.5L饮用水\",\"能量棒\",\"急救包\"],\"specialTips\":\"夏季注意防蚊\"}," +
                "\n\"supplyInfo\":{\"waterPoints\":[\"3km处溪流\"],\"shops\":[\"起点旁小卖部\"],\"restaurants\":[]}" +
                "\n}";
    }

    /**
     * 徒步分片2：探险挑战徒步路线
     */
    private String buildHikingShard2Prompt(String destination, String common, String hikingParams, String hotData) {
        return "你是专业徒步路线规划师。只输出JSON，无其他内容。\n\n" +
                common + hikingParams + hotData +
                "\n\n请为" + destination + "规划一条「探险挑战徒步路线」。" +
                "\n要求：路线有一定难度和野趣，包含原生态路段、特色地貌，适合喜欢探索的徒步爱好者。" +
                "\n将整条路线按路段拆解，每段标注距离、路况、坡度、亮点景观、休息点、风险提示。" +
                "\n\n输出格式同分片1（routeName改为「探险挑战徒步路线」，路线特色不同）。";
    }

    /**
     * 徒步分片3：经典环线徒步路线
     */
    private String buildHikingShard3Prompt(String destination, String common, String hikingParams, String hotData) {
        return "你是专业徒步路线规划师。只输出JSON，无其他内容。\n\n" +
                common + hikingParams + hotData +
                "\n\n请为" + destination + "规划一条「经典环线徒步路线」。" +
                "\n要求：起点终点相同，路线成熟、标志清晰、难度适中，适合大多数徒步爱好者，交通便利。" +
                "\n将整条路线按路段拆解，每段标注距离、路况、坡度、亮点景观、休息点、风险提示。" +
                "\n\n输出格式同分片1（routeName改为「经典环线徒步路线」，路线特色不同）。";
    }

    /**
     * 徒步分片4：装备/安全/补给全套攻略（3套风格）
     */
    private String buildHikingShard4Prompt(String destination, String common, String hikingParams, String hotData) {
        return "你是专业户外领队和装备顾问。只输出JSON，无其他内容。\n\n" +
                common + hikingParams + hotData +
                "\n\n请为" + destination + "徒步提供 3 套装备/安全/补给攻略：" +
                "\n① scenic：对应「风景休闲」路线的装备建议（轻装出行，基础装备）" +
                "\n② adventure：对应「探险挑战」路线的装备建议（专业装备，安全防护）" +
                "\n③ classic：对应「经典环线」路线的装备建议（中等装备，平衡舒适与安全）" +
                "\n\n每套需要包含：衣物列表(clothing)、必带物资(supplies)、特殊提醒(specialTips)、" +
                "\n安全注意事项(safetyTips)、推荐出发时间(bestStartTime)、预计花费(costEstimate)。" +
                "\n\n请按以下 JSON 格式输出（只输出JSON，不要其他文字）：" +
                "\n{\"scenic\":{\"clothing\":[\"速干T恤\",\"运动裤\"],\"supplies\":[\"1.5L水\",\"能量棒\"]," +
                "\n  \"specialTips\":\"夏季注意防晒\",\"safetyTips\":\"走台阶路注意防滑\"," +
                "\n  \"bestStartTime\":\"早上7:00\",\"costEstimate\":\"门票+交通约50元\"}," +
                "\n \"adventure\":{...同结构...}," +
                "\n \"classic\":{...同结构...}" +
                "\n}";
    }

    // ==================== AI 调用 ====================

    /**
     * 调用 AI 接口（复用连接池，不重复创建 HTTP 客户端）
     */
    private String callAI(String prompt, int maxTokens, double temperature) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是专业旅行规划师，只输出JSON，无其他内容。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String response = restTemplate.postForObject(apiUrl, entity, String.class);
        return parseAIResponse(response);
    }

    private String parseAIResponse(String response) {
        try {
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode choice = choices.get(0);
                String content = choice.get("message").get("content").asText();
                
                // 检查完成原因，诊断是否被截断
                String finishReason = choice.has("finish_reason") ? 
                    choice.get("finish_reason").asText() : "unknown";
                log.info("AI响应: finishReason={}, 内容长度={}字符", finishReason, content.length());
                if ("length".equals(finishReason)) {
                    log.warn("⚠️ AI因token不足被截断（finish_reason=length），输出可能不完整！建议增加max_tokens");
                }
                
                int jsonStart = content.indexOf('{');
                int jsonEnd = content.lastIndexOf('}');
                
                if (jsonStart >= 0 && jsonEnd >= jsonStart) {
                    String jsonStr = content.substring(jsonStart, jsonEnd + 1);
                    
                    // 验证 JSON 是否有效
                    try {
                        JsonNode parsed = objectMapper.readTree(jsonStr);
                        // 检查days数组长度
                        if (parsed.has("days") && parsed.get("days").isArray()) {
                            int daysSize = parsed.get("days").size();
                            log.info("AI返回JSON: days数组长度={}, finishReason={}", daysSize, finishReason);
                        } else if (parsed.has("routeName")) {
                            log.info("AI返回徒步路线JSON, finishReason={}", finishReason);
                        }
                        return jsonStr;
                    } catch (Exception e) {
                        log.warn("提取的 JSON 无效，尝试修复：{}", e.getMessage());
                        // 尝试修复常见的 JSON 错误
                        return tryFixJson(jsonStr);
                    }
                } else {
                    throw new RuntimeException("AI 响应中未提取到有效的 JSON 数据");
                }
            } else {
                throw new RuntimeException("AI 响应格式异常：无有效 choices");
            }
        } catch (Exception e) {
            log.error("解析 AI 响应失败: {}", e.getMessage());
            throw new RuntimeException("AI 响应解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 尝试修复不完整的 JSON
     */
    private String tryFixJson(String json) {
        // 策略1：尝试找到完整的days数组结束位置
        try {
            int daysIndex = json.indexOf("\"days\"");
            if (daysIndex > 0) {
                // 从days数组开始位置，尝试找到匹配的数组结束
                int bracketCount = 0;
                int braceCount = 0;
                boolean inDaysArray = false;
                int lastValidEnd = -1;
                
                for (int i = daysIndex; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '[') {
                        bracketCount++;
                        if (bracketCount == 1 && json.substring(Math.max(0, i-10), i).contains("days")) {
                            inDaysArray = true;
                        }
                    } else if (c == ']') {
                        bracketCount--;
                        if (inDaysArray && bracketCount == 0) {
                            // 找到了days数组的结束
                            lastValidEnd = i;
                            break;
                        }
                    } else if (c == '{') {
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0 && lastValidEnd > 0) {
                            // 找到了完整JSON的结束
                            String fixed = json.substring(0, i + 1);
                            try {
                                objectMapper.readTree(fixed);
                                return fixed;
                            } catch (Exception e) {
                                // 继续尝试
                            }
                        }
                    }
                }
                
                // 如果找到了days数组结束，尝试构建完整JSON
                if (lastValidEnd > 0) {
                    String fixed = json.substring(0, lastValidEnd + 1) + "}";
                    try {
                        objectMapper.readTree(fixed);
                        return fixed;
                    } catch (Exception e) {
                        // 继续尝试其他策略
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        
        // 策略2：尝试截断到最后一个完整的day对象
        try {
            String pattern = "\"day\":";
            int lastDayIndex = json.lastIndexOf(pattern);
            if (lastDayIndex > 0) {
                // 找到这个day对象的结束
                int braceCount = 0;
                boolean started = false;
                for (int i = lastDayIndex; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '{') {
                        braceCount++;
                        started = true;
                    } else if (c == '}') {
                        braceCount--;
                        if (started && braceCount == 0) {
                            // 找到了day对象的结束
                            String prefix = json.substring(0, i + 1);
                            // 检查是否有days数组
                            if (prefix.contains("\"days\"")) {
                                String fixed = prefix + "]}";
                                try {
                                    objectMapper.readTree(fixed);
                                    return fixed;
                                } catch (Exception e) {
                                    // 继续尝试
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        
        // 策略3：原始方法（兜底）
        try {
            int lastValidEnd = json.lastIndexOf("}");
            if (lastValidEnd > 0) {
                String fixed = json.substring(0, lastValidEnd + 1);
                objectMapper.readTree(fixed);
                return fixed;
            }
        } catch (Exception e) {
            // 忽略
        }
        
        throw new RuntimeException("无法修复无效的 JSON 响应");
    }

    // ==================== 默认空数据生成 ====================

    /**
     * 生成默认空行程 JSON
     */
    private String generateEmptyDaysJson(String destination, int days, String startDate) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode result = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode daysArray = objectMapper.createArrayNode();
            LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE);
            for (int i = 1; i <= days; i++) {
                com.fasterxml.jackson.databind.node.ObjectNode day = objectMapper.createObjectNode();
                day.put("day", i);
                day.put("date", start.plusDays(i - 1).format(DateTimeFormatter.ISO_DATE));
                day.put("weather", "晴");
                day.put("temperature", "25°C");
                day.set("points", objectMapper.createArrayNode());
                com.fasterxml.jackson.databind.node.ObjectNode hotel = objectMapper.createObjectNode();
                hotel.put("name", "");
                hotel.put("detail", "");
                hotel.put("price", 0);
                day.set("hotel", hotel);
                daysArray.add(day);
            }
            result.set("days", daysArray);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"days\":[]}";
        }
    }

    /**
     * 生成默认空食宿 JSON
     */
    private String generateEmptyShard4Json(String destination, int days) {
        String emptyDaysJson = generateEmptyDaysJson(destination, days, LocalDate.now().format(DateTimeFormatter.ISO_DATE));
        return "{\"seasonal\":" + emptyDaysJson + ",\"trendy\":" + emptyDaysJson + ",\"classic\":" + emptyDaysJson + "}";
    }

    // ==================== 工具方法 ====================

    private String getPreferenceText(String preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return "休闲放松";
        }
        StringBuilder result = new StringBuilder();
        String[] prefs = preferences.split(",");
        for (int i = 0; i < prefs.length; i++) {
            if (i > 0) result.append("、");
            switch (prefs[i].trim()) {
                case "leisure": result.append("休闲放松"); break;
                case "photo": result.append("拍照打卡"); break;
                case "niche": result.append("小众深度"); break;
                case "culture": result.append("文化历史"); break;
                case "food": result.append("美食探索"); break;
                case "adventure": result.append("冒险刺激"); break;
                default: result.append(prefs[i]);
            }
        }
        return result.toString();
    }

    private String getBudgetText(String budget) {
        if (budget == null || budget.isEmpty()) return "中端";
        switch (budget) {
            case "economy": return "经济";
            case "medium": return "中端";
            case "luxury": return "高端";
            default: return budget;
        }
    }

    private String getTravelModeText(String travelMode) {
        if (travelMode == null || travelMode.isEmpty()) return "自驾";
        switch (travelMode) {
            case "drive": return "自驾";
            case "walk": return "步行";
            case "hiking": return "徒步";
            case "transit": return "公共交通";
            case "bike": return "骑行";
            default: return travelMode;
        }
    }

    @Override
    public String generateSpotDescription(String spotName, String category) {
        String prompt = String.format("请为景点'%s'（类型：%s）生成一段 50 字左右的介绍文字",
                spotName, category);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", prompt));

            requestBody.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 复用共享 RestTemplate 连接池
            String response = restTemplate.postForObject(apiUrl, entity, String.class);

            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                return choices.get(0).get("message").get("content").asText();
            }
        } catch (Exception e) {
            log.error("AI 生成景点描述失败", e);
        }

        return "这是一个值得一游的景点。";
    }

    @Override
    public String generatePackingList(String destination, int days, String travelMode, String weatherInfo) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个旅行打包专家。请为以下旅行生成一份详细的行李清单。\n\n");
        prompt.append("目的地：").append(destination).append("\n");
        prompt.append("天数：").append(days).append("天\n");
        prompt.append("出行方式：").append(travelMode).append("\n");
        if (weatherInfo != null && !weatherInfo.isEmpty()) {
            prompt.append("天气：").append(weatherInfo).append("\n");
        }

        prompt.append("\n请按以下 JSON 格式返回行李清单（只返回 JSON，不要其他文字）：\n");
        prompt.append("{\n");
        prompt.append("  \"categories\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"分类名称\",\n");
        prompt.append("      \"items\": [\n");
        prompt.append("        { \"name\": \"物品名称\", \"quantity\": 数量, \"checked\": false, \"note\": \"备注（可选）\" }\n");
        prompt.append("      ]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("分类建议包括：证件类、衣物类、洗护类、电子设备类、医疗保健类、其他类等。");
        prompt.append("基于目的地天气和旅行天数为每位物品推荐合理数量，如果是自驾/骑行需补充车载用品。");

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", prompt.toString()));
            requestBody.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForEntity(apiUrl, entity, String.class).getBody();

            if (response != null) {
                JsonNode jsonResponse = objectMapper.readTree(response);
                JsonNode choices = jsonResponse.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    String content = choices.get(0).get("message").get("content").asText();
                    // 健壮提取 JSON：去掉 markdown 代码块，再找第一个 { 到最后一个 }
                    String json = extractJson(content);
                    // 验证是有效 JSON
                    objectMapper.readTree(json);
                    log.info("行李清单生成成功，目的地={}，天数={}", destination, days);
                    return json;
                }
            }
        } catch (Exception e) {
            log.error("AI 生成行李清单失败", e);
        }

        // 失败时返回默认清单
        return "{\"categories\":[{\"name\":\"证件类\",\"items\":[{\"name\":\"身份证\",\"quantity\":1,\"checked\":false}]},{\"name\":\"衣物类\",\"items\":[{\"name\":\"外套\",\"quantity\":1,\"checked\":false},{\"name\":\"换洗衣物\",\"quantity\":" + days + ",\"checked\":false}]},{\"name\":\"洗护类\",\"items\":[{\"name\":\"洗漱包\",\"quantity\":1,\"checked\":false}]},{\"name\":\"电子设备\",\"items\":[{\"name\":\"手机充电器\",\"quantity\":1,\"checked\":false}]}]}";
    }

    /**
     * 从 AI 返回文本中健壮提取 JSON 字符串
     * 处理场景：markdown 代码块、前后说明文字、多个 { } 嵌套
     */
    private String extractJson(String text) {
        if (text == null) return "{}";
        String s = text.trim();

        // 1. 去掉 ```json / ``` 代码块
        s = s.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)\\s*```", "");

        // 2. 找到第一个 '{' 和最后一个 '}'，截取中间部分
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }

        // 3. 如果前面还有非 JSON 文字，再找一次
        if (!s.startsWith("{")) {
            int idx = s.indexOf('{');
            if (idx >= 0) s = s.substring(idx);
        }

        return s.trim();
    }
}
