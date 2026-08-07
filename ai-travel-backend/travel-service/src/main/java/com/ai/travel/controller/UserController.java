package com.ai.travel.controller;

import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.dto.UserProfileDTO;
import com.ai.travel.config.RequireLogin;
import com.ai.travel.config.UserContext;
import com.ai.travel.service.SocialService;
import com.ai.travel.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@RequireLogin
public class UserController {

    private final UserService userService;
    private final SocialService socialService;

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @GetMapping("/profile")
    public ApiResponse<UserProfileDTO> getUserProfile() {
        String userId = UserContext.getCurrentUserId();
        UserProfileDTO profile = userService.getUserProfile(userId);
        if (profile == null) {
            return ApiResponse.error("用户不存在");
        }
        return ApiResponse.success(profile);
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<Map<String, Object>> getPublicUserProfile(@PathVariable String userId) {
        String currentUserId = UserContext.getCurrentUserId();
        UserProfileDTO profile = userService.getUserProfile(userId);
        if (profile == null) {
            return ApiResponse.error("用户不存在");
        }
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("profile", profile);
        data.put("stats", userService.getUserStats(userId));
        data.put("isSelf", currentUserId.equals(userId));
        data.put("isFollowed", !currentUserId.equals(userId) && socialService.isFollowed(currentUserId, userId));
        return ApiResponse.success(data);
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileDTO> updateUserProfile(@RequestBody UserProfileDTO userProfileDTO) {
        String userId = UserContext.getCurrentUserId();
        UserProfileDTO profile = userService.updateUserProfile(userId, userProfileDTO);
        return ApiResponse.success(profile, "资料更新成功");
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getUserStats() {
        String userId = UserContext.getCurrentUserId();
        Map<String, Object> stats = userService.getUserStats(userId);
        return ApiResponse.success(stats);
    }

    @GetMapping("/preferences")
    public ApiResponse<String> getPreferences() {
        String userId = UserContext.getCurrentUserId();
        String prefs = userService.getPreferences(userId);
        return ApiResponse.success(prefs);
    }

    @PutMapping("/preferences")
    public ApiResponse<Void> savePreferences(@RequestBody Map<String, Object> preferences) {
        String userId = UserContext.getCurrentUserId();
        String json = com.alibaba.fastjson2.JSON.toJSONString(preferences);
        userService.savePreferences(userId, json);
        return ApiResponse.success(null, "偏好设置保存成功");
    }

    @GetMapping("/settings")
    public ApiResponse<String> getSettings() {
        String userId = UserContext.getCurrentUserId();
        String settings = userService.getSettings(userId);
        return ApiResponse.success(settings);
    }

    @PutMapping("/settings")
    public ApiResponse<Void> saveSettings(@RequestBody Map<String, Object> settings) {
        String userId = UserContext.getCurrentUserId();
        String json = com.alibaba.fastjson2.JSON.toJSONString(settings);
        userService.saveSettings(userId, json);
        return ApiResponse.success(null, "系统设置保存成功");
    }

    @GetMapping("/footprint")
    public ApiResponse<List<String>> getFootprint() {
        String userId = UserContext.getCurrentUserId();
        List<String> cities = userService.getFootprint(userId);
        return ApiResponse.success(cities);
    }

    @GetMapping("/footprint/detail")
    public ApiResponse<List<Map<String, Object>>> getFootprintDetail() {
        String userId = UserContext.getCurrentUserId();
        return ApiResponse.success(userService.getFootprintDetail(userId));
    }

    @PostMapping("/avatar")
    public ApiResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error("请选择图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.error("只能上传图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ApiResponse.error("图片大小不能超过 5MB");
        }

        String userId = UserContext.getCurrentUserId();
        String ext = contentType.contains("png") ? "png" : "jpg";
        String fileName = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;

        try {
            Path dir = Paths.get(uploadPath, "avatars");
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            file.transferTo(target.toFile());
            String avatarUrl = "/uploads/avatars/" + fileName;
            userService.updateAvatar(userId, avatarUrl);
            return ApiResponse.success(avatarUrl, "头像上传成功");
        } catch (IOException e) {
            log.error("头像上传失败", e);
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/cover")
    public ApiResponse<String> uploadCover(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error("请选择图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.error("只能上传图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ApiResponse.error("图片大小不能超过 5MB");
        }

        String userId = UserContext.getCurrentUserId();
        String ext = contentType.contains("png") ? "png" : "jpg";
        String fileName = userId + "_cover_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;

        try {
            Path dir = Paths.get(uploadPath, "covers");
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            file.transferTo(target.toFile());
            String coverUrl = "/uploads/covers/" + fileName;
            userService.updateCoverImage(userId, coverUrl);
            return ApiResponse.success(coverUrl, "封面上传成功");
        } catch (IOException e) {
            log.error("封面上传失败", e);
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }

    @PutMapping("/cover")
    public ApiResponse<String> updateCover(@RequestBody Map<String, String> body) {
        String coverImage = body == null ? null : body.get("coverImage");
        if (coverImage == null || coverImage.trim().isEmpty()) {
            return ApiResponse.error("封面地址不能为空");
        }
        String userId = UserContext.getCurrentUserId();
        userService.updateCoverImage(userId, coverImage.trim());
        return ApiResponse.success(coverImage.trim(), "封面更新成功");
    }
}
