package com.ai.travel.controller;

import com.ai.travel.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final RestTemplate restTemplate;

    @Value("${pixabay.api.key}")
    private String pixabayApiKey;

    private static final String PIXABAY_BASE = "https://pixabay.com/api";

    /**
     * 搜索 Pixabay 图片
     */
    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> searchImages(
            @RequestParam String keyword) {
        String url = UriComponentsBuilder.fromHttpUrl(PIXABAY_BASE)
                .queryParam("key", pixabayApiKey)
                .queryParam("q", keyword)
                .queryParam("image_type", "photo")
                .queryParam("orientation", "vertical")
                .queryParam("per_page", "3")
                .queryParam("safesearch", "true")
                .toUriString();

        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("Pixabay 图片搜索失败: {}", e.getMessage());
            return ApiResponse.error("图片搜索失败: " + e.getMessage());
        }
    }
}
