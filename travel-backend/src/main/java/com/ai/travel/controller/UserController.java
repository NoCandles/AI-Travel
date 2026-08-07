package com.ai.travel.controller;

import com.ai.travel.dto.ApiResponse;
import com.ai.travel.dto.UserProfileDTO;
import com.ai.travel.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ApiResponse<UserProfileDTO> getUserProfile(
            @RequestHeader("X-User-Id") String userId) {
        UserProfileDTO profile = userService.getUserProfile(userId);
        if (profile == null) {
            return ApiResponse.error("用户不存在");
        }
        return ApiResponse.success(profile);
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileDTO> updateUserProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody UserProfileDTO userProfileDTO) {
        UserProfileDTO profile = userService.updateUserProfile(userId, userProfileDTO);
        return ApiResponse.success(profile, "资料更新成功");
    }

    /**
     * 获取用户统计数据（行程、发布、收藏、关注）
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getUserStats(
            @RequestHeader("X-User-Id") String userId) {
        Map<String, Object> stats = userService.getUserStats(userId);
        return ApiResponse.success(stats);
    }

    /**
     * 获取用户偏好设置
     */
    @GetMapping("/preferences")
    public ApiResponse<String> getPreferences(
            @RequestHeader("X-User-Id") String userId) {
        String prefs = userService.getPreferences(userId);
        return ApiResponse.success(prefs);
    }

    /**
     * 保存用户偏好设置
     */
    @PutMapping("/preferences")
    public ApiResponse<Void> savePreferences(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> preferences) {
        String json = com.alibaba.fastjson2.JSON.toJSONString(preferences);
        userService.savePreferences(userId, json);
        return ApiResponse.success(null, "偏好设置保存成功");
    }

    /**
     * 获取用户足迹（去过的不重复城市列表）
     */
    @GetMapping("/footprint")
    public ApiResponse<List<String>> getFootprint(@RequestHeader("X-User-Id") String userId) {
        List<String> cities = userService.getFootprint(userId);
        return ApiResponse.success(cities);
    }

    /**
     * 获取用户足迹详情（目的地 + 次数）
     */
    @GetMapping("/footprint/detail")
    public ApiResponse<List<Map<String, Object>>> getFootprintDetail(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(userService.getFootprintDetail(userId));
    }
}
