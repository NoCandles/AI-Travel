package com.ai.travel.service.impl;

import com.ai.travel.repository.SensitiveWordRepository;
import com.ai.travel.service.SensitiveWordService;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordRepository sensitiveWordRepository;

    /** 缓存的敏感词列表 */
    private List<String> cachedWords = null;

    @PostConstruct
    public void init() {
        reloadCache();
    }

    @Override
    public String checkText(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }

        List<String> words = getSensitiveWords();
        if (words.isEmpty()) {
            return null;
        }

        // 全量匹配检查
        for (String word : words) {
            if (text.contains(word)) {
                log.info("检测到敏感词: [{}] 在文本中: {}", word, text.substring(0, Math.min(50, text.length())));
                return word;
            }
        }
        return null;
    }

    /**
     * 获取敏感词列表（带缓存）
     */
    private List<String> getSensitiveWords() {
        if (cachedWords == null) {
            reloadCache();
        }
        return cachedWords;
    }

    /**
     * 重新加载缓存
     */
    public void reloadCache() {
        cachedWords = sensitiveWordRepository.selectList(null)
                .stream()
                .map(sw -> sw.getWord())
                .filter(w -> StringUtils.isNotBlank(w))
                .collect(Collectors.toList());
        log.info("敏感词缓存已加载，共 {} 个词", cachedWords.size());
    }
}
