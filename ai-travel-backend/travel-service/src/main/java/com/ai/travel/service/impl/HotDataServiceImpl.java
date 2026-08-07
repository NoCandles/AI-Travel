package com.ai.travel.service.impl;

import com.ai.travel.service.HotDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 热点数据服务实现
 * <p>
 * 使用内存缓存（1 小时 TTL），首次访问时异步抓取热门信息。
 * 抓取失败返回空字符串，不阻塞主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotDataServiceImpl implements HotDataService {

    /** 内存缓存：key = destination_month, value = CacheEntry */
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Qualifier("tripGenerationExecutor")
    private final Executor tripGenerationExecutor;

    /** 缓存 TTL：1 小时 */
    private static final long CACHE_TTL_MINUTES = 60;

    /** 网络抓取超时：5 秒 */
    private static final int FETCH_TIMEOUT_MS = 5000;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.url}")
    private String apiUrl;

    @Override
    public String getHotTravelData(String destination, String month) {
        String cacheKey = buildCacheKey(destination, month);

        // 1. 检查缓存
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            log.debug("热点数据缓存命中：{}", cacheKey);
            return entry.getData();
        }

        // 2. 异步抓取（使用共享 RestTemplate 连接池）
        CompletableFuture.supplyAsync(() -> fetchHotData(destination, month), tripGenerationExecutor)
                .thenAccept(data -> {
                    cache.put(cacheKey, new CacheEntry(data));
                    log.info("热点数据已缓存：{}，长度={}", cacheKey, data.length());
                })
                .exceptionally(e -> {
                    log.warn("热点数据抓取失败，将使用空数据：{}", e.getMessage());
                    cache.put(cacheKey, new CacheEntry(""));
                    return null;
                });

        // 3. 立即返回空（数据将在下次请求时从缓存获取）
        log.info("热点数据首次请求，触发异步抓取：{}", cacheKey);
        return "";
    }

    // ==================== 私有方法 ====================

    private String buildCacheKey(String destination, String month) {
        return (destination + "_" + month).replaceAll("\\s+", "_");
    }

    /**
     * 抓取热点数据
     */
    private String fetchHotData(String destination, String month) {
        // 方式 1：用 AI 生成热点参考（更可控）
        String aiHotData = fetchHotDataFromAI(destination, month);
        if (!aiHotData.isEmpty()) {
            return aiHotData;
        }

        // 方式 2：网络抓取（备用）
        return fetchHotDataFromWeb(destination, month);
    }

    /**
     * 用 AI 生成目的地当月热点参考
     */
    private String fetchHotDataFromAI(String destination, String month) {
        try {
            String prompt = String.format(
                    "请提供%s在%s的以下信息（简洁，每条一行）：\n" +
                    "1. 当月最佳旅行时间/气候特点\n" +
                    "2. 当月特色活动/节庆\n" +
                    "3. 3-5个当月最热门景点\n" +
                    "4. 3-5个当季美食推荐\n" +
                    "5. 当月网红打卡地推荐\n" +
                    "只需输出信息，不要多余话。",
                    destination, month
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("temperature", 0.5);
            requestBody.put("max_tokens", 1000);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "你是旅行热点分析师，只输出信息，不要多余文字。"));
            messages.add(Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 复用共享 RestTemplate 连接池
            String response = restTemplate.postForObject(apiUrl, entity, String.class);
            String content = parseAIResponse(response);

            if (content != null && !content.isEmpty() && !"{}".equals(content)) {
                log.info("AI 热点数据生成成功：{}，长度={}", destination, content.length());
                return "【" + month + " " + destination + "旅行热点】\n" + content;
            }
        } catch (Exception e) {
            log.warn("AI 生成热点数据失败：{}", e.getMessage());
        }
        return "";
    }

    /**
     * 网络抓取热门数据（备用来源）
     */
    private String fetchHotDataFromWeb(String destination, String month) {
        StringBuilder result = new StringBuilder();
        result.append("【").append(month).append(" ").append(destination).append("旅行热点】\n");

        try {
            String encodedDest = URLEncoder.encode(destination + "旅游攻略", StandardCharsets.UTF_8);
            String url = "https://www.mafengwo.cn/search/q.php?q=" + encodedDest;

            Document doc = Jsoup.connect(url)
                    .timeout(FETCH_TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();

            String title = doc.title();
            if (title != null && !title.isEmpty()) {
                result.append("马蜂窝攻略：").append(title).append("\n");
            }

            result.append(month).append("推荐前往").append(destination).append("旅游。");
            log.info("网页热点数据抓取成功：{}", destination);
        } catch (IOException e) {
            log.warn("马蜂窝抓取失败：{}", e.getMessage());
            result.append(month).append("是游览").append(destination).append("的好时节。");
        }

        return result.toString();
    }

    /**
     * 解析 AI 返回内容
     */
    private String parseAIResponse(String response) {
        try {
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                return choices.get(0).get("message").get("content").asText();
            }
        } catch (Exception e) {
            log.warn("解析 AI 响应失败：{}", e.getMessage());
        }
        return "";
    }

    // ==================== 缓存条目 ====================

    private static class CacheEntry {
        private final String data;
        private final LocalDateTime createdAt;

        CacheEntry(String data) {
            this.data = data;
            this.createdAt = LocalDateTime.now();
        }

        String getData() {
            return data;
        }

        boolean isExpired() {
            return Duration.between(createdAt, LocalDateTime.now()).toMinutes() >= CACHE_TTL_MINUTES;
        }
    }
}
