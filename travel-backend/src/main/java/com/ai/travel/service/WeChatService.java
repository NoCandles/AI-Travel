package com.ai.travel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 微信服务
 * <p>
 * 用于与微信服务器交互，包括：
 * 1. code2session - 用 code 换取 openId
 */
@Slf4j
@Service
public class WeChatService {

    @Value("${wechat.app-id}")
    private String appId;

    @Value("${wechat.app-secret}")
    private String appSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session" +
            "?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    /**
     * 用 wx.login() 返回的 code 换取 openId
     *
     * @param code 前端 wx.login() 返回的 code
     * @return openId，失败返回 null
     */
    public String getOpenId(String code) {
        if (code == null || code.isEmpty()) {
            log.warn("微信 code 为空");
            return null;
        }

        String url = String.format(CODE2SESSION_URL, appId, appSecret, code);

        try {
            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                log.error("微信 code2session 返回空");
                return null;
            }

            JsonNode json = objectMapper.readTree(response);

            // 检查是否有错误
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                log.error("微信 code2session 失败：errcode={}, errmsg={}",
                        json.get("errcode").asInt(),
                        json.get("errmsg").asText());
                return null;
            }

            String openId = json.get("openid").asText();
            log.info("获取微信 openId 成功：{}", openId);
            return openId;

        } catch (Exception e) {
            log.error("微信 code2session 异常", e);
            return null;
        }
    }
}
