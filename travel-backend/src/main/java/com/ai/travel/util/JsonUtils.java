package com.ai.travel.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON 处理工具类
 */
public class JsonUtils {

    /**
     * 安全读取 JSON 节点字段值，不存在或为 null 时返回默认值
     */
    public static String getSafeText(JsonNode node, String field, String defaultValue) {
        if (node.has(field)) {
            JsonNode value = node.get(field);
            return value != null && !value.isNull() ? value.asText() : defaultValue;
        }
        return defaultValue;
    }
}
