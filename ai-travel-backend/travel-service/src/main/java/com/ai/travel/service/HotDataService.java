package com.ai.travel.service;

/**
 * 热点数据服务 — 提前抓取目的地当月网红路线、热门景点并缓存
 * <p>
 * 缓存数据直接注入 Prompt，让 AI 无需联网查热点。
 */
public interface HotDataService {

    /**
     * 获取目的地当月热点旅行数据
     * @param destination 目的地名称
     * @param month 月份，如 "6月"
     * @return 热点描述文本（可直接嵌入 Prompt），失败时返回空字符串
     */
    String getHotTravelData(String destination, String month);
}
