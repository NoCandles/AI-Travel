package com.ai.travel.service;

/**
 * 敏感词校验服务
 */
public interface SensitiveWordService {

    /**
     * 检查文本是否包含敏感词
     *
     * @param text 待检查的文本
     * @return 如果包含敏感词返回匹配到的第一个敏感词，否则返回 null
     */
    String checkText(String text);
}
