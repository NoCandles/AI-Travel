package com.ai.travel.service;

public interface ImageService {
    /**
     * 从 Pixabay 搜索图片
     * @param keyword 搜索关键词
     * @return 图片 URL
     */
    String searchImageFromPixabay(String keyword);
}
