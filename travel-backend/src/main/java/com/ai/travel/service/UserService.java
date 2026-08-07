package com.ai.travel.service;

import com.ai.travel.dto.UserProfileDTO;
import java.util.List;
import java.util.Map;

public interface UserService {

    UserProfileDTO getUserProfile(String userId);

    UserProfileDTO updateUserProfile(String userId, UserProfileDTO userProfileDTO);

    /** 获取用户统计数据（行程、发布、收藏、关注） */
    Map<String, Object> getUserStats(String userId);

    /** 获取用户偏好设置 JSON */
    String getPreferences(String userId);

    /** 保存用户偏好设置 JSON */
    void savePreferences(String userId, String preferencesJson);

    /** 获取用户足迹（去过的不重复目的地列表） */
    List<String> getFootprint(String userId);

    /** 获取用户足迹详情（目的地 + 次数） */
    List<Map<String, Object>> getFootprintDetail(String userId);
}
