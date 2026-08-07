package com.ai.travel.service.impl;

import com.ai.travel.service.ImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    private final RestTemplate restTemplate;

    public ImageServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${pixabay.api.key:}")
    private String pixabayApiKey;

    private static final String PIXABAY_BASE = "https://pixabay.com/api";

    @Override
    public String searchImageFromPixabay(String keyword) {
        if (pixabayApiKey == null || pixabayApiKey.trim().isEmpty()) {
            log.warn("Pixabay API Key 未配置");
            return "";
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(PIXABAY_BASE)
                    .queryParam("key", pixabayApiKey)
                    .queryParam("q", keyword)
                    .queryParam("image_type", "photo")
                    .queryParam("orientation", "landscape")
                    .queryParam("safesearch", true)
                    .queryParam("per_page", 3)
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("hits")) {
                java.util.List<?> hits = (java.util.List<?>) response.get("hits");
                if (hits != null && !hits.isEmpty()) {
                    Map<String, Object> firstHit = (Map<String, Object>) hits.get(0);
                    String imageUrl = (String) firstHit.get("largeImageURL");
                    log.debug("Pixabay 搜索 '{}' 成功", keyword);
                    return imageUrl != null ? imageUrl : "";
                }
            }
            
            log.debug("Pixabay 未找到图片：{}", keyword);
            return "";
        } catch (Exception e) {
            log.debug("Pixabay 搜索失败：{}", keyword);
            return "";
        }
    }
}
