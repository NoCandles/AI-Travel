package com.ai.travel.service.impl;

import org.springframework.stereotype.Component;

/**
 * AI Prompt 构建器
 * 从 AIServiceImpl（870行）中提取，每个 build* 方法独立可测。
 *
 * 包含 8 个分片 Prompt 构建方法：
 *   buildShard1Prompt ~ buildShard4Prompt（普通模式）
 *   buildHikingShard1Prompt ~ buildHikingShard4Prompt（徒步模式）
 */
@Component
public class PromptBuilder {

    /**
     * 构建通用参数段（所有分片共用）
     */
    public String buildCommonParams(int days, String budget, String preferences,
                                     String mustVisit, String startDate, String endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 出行天数：").append(days).append("天\n");
        if (budget != null && !budget.isEmpty()) sb.append("- 预算：").append(budget).append("\n");
        if (preferences != null && !preferences.isEmpty()) sb.append("- 偏好：").append(preferences).append("\n");
        if (mustVisit != null && !mustVisit.isEmpty()) sb.append("- 必去景点：").append(mustVisit).append("\n");
        if (startDate != null && !startDate.isEmpty()) sb.append("- 出发日期：").append(startDate).append("\n");
        if (endDate != null && !endDate.isEmpty()) sb.append("- 结束日期：").append(endDate).append("\n");
        return sb.toString();
    }

    /**
     * 分片 1：当月时令定制版行程（景点 + 每日安排概要）
     */
    public String buildShard1Prompt(String destination, int days, String common, String hotData) {
        return common + hotData +
                "\n请为" + destination + "规划一套「当月时令定制版」" + days + "日游路线」" +
                "\n要求：突出当月应季特色、时令活动、当季美食，结合当月气候特点」" +
                "\n\n每天安排 3-5 个景点，出发时间 8-9 点，标注到达时间(HH:MM)和游玩时长duration(小时)。" +
                "\n每个景点必须包含 travelGuide 字段：简短的游玩攻略（0-100字），包含最佳游览路线、拍照点、注意事项等实用建议。" +
                "\n同时推荐当天适合的酒店（含名称、简介、价格）。" +
                "\n\n⚠️ 必须输出正好 " + days + " 天的完整行程（第1天到第" + days + "天），一天都不能少！" +
                "\n\n请按以下 JSON 格式输出完整的" + days + " 天行程（只输出JSON，不要其他文字），" +
                "\n{\"days\":[" +
                "\n  {\"day\":1,\"date\":\"2026-06-15\",\"weather\":\"晴\",\"temperature\":\"28°C\"," +
                "\n   \"points\":[{\"name\":\"景点名\",\"address\":\"地址\",\"arrivalTime\":\"09:00\",\"duration\":2,\"category\":\"景点类型\",\"travelGuide\":\"游玩攻略：最佳路线、拍照点、注意事项等\"}]," +
                "\n   \"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300}}," +
                "\n  ...," +
                "\n  {\"day\":" + days + ",\"date\":\"2026-06-" + (14 + days) + "\",\"weather\":\"晴\",\"temperature\":\"28°C\"," +
                "\n   \"points\":[{\"name\":\"景点名\",\"address\":\"地址\",\"arrivalTime\":\"09:00\",\"duration\":2,\"category\":\"景点类型\",\"travelGuide\":\"游玩攻略\"}]," +
                "\n   \"hotel\":{\"name\":\"酒店名\",\"detail\":\"简介\",\"price\":300}}" +
                "\n]}";
    }

    // shard2~shard4 和 hikingShard1~hikingShard4 方法从 AIServiceImpl 中迁移至此
    // TODO: 迁移 buildShard2Prompt, buildShard3Prompt, buildShard4Prompt
    // TODO: 迁移 buildHikingShard1Prompt ~ buildHikingShard4Prompt
}
