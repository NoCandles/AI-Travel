package com.ai.travel.controller;

import com.ai.travel.entity.*;
import com.ai.travel.repository.LevelConfigRepository;
import com.ai.travel.dto.ApiResponse;
import com.ai.travel.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {
    
    private final PointsService pointsService;
    private final LevelConfigRepository levelConfigRepository;
    
    @GetMapping("/my")
    public ApiResponse<UserPoints> getMyPoints(
            @RequestHeader("X-User-Id") String userId) {
        UserPoints points = pointsService.getUserPoints(userId);
        return ApiResponse.success(points);
    }
    
    @PostMapping("/sign-in")
    public ApiResponse<Map<String, Object>> signIn(
            @RequestHeader("X-User-Id") String userId) {
        Map<String, Object> result = pointsService.signIn(userId);
        return ApiResponse.success(result, "签到成功");
    }
    
    @GetMapping("/records")
    public ApiResponse<List<PointRecord>> getRecords(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PointRecord> records = pointsService.getPointRecords(userId, page, size);
        return ApiResponse.success(records);
    }
    
    @PostMapping("/earn")
    public ApiResponse<Void> earnPoints(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody EarnPointsRequest request) {
        pointsService.earnPoints(userId, request.getSource(), 
            request.getRelatedId(), request.getDescription());
        return ApiResponse.success(null, "积分已添加");
    }
    
    @PostMapping("/spend")
    public ApiResponse<Void> spendPoints(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody SpendPointsRequest request) {
        pointsService.spendPoints(userId, request.getSource(), 
            request.getRelatedId(), request.getDescription());
        return ApiResponse.success(null, "积分已扣除");
    }
    
    @GetMapping("/level-config")
    public ApiResponse<List<LevelConfig>> getLevelConfig() {
        // 返回所有等级配置
        List<LevelConfig> levels = levelConfigRepository.selectList(null);
        return ApiResponse.success(levels);
    }
    
    @PostMapping("/unlock-template")
    public ApiResponse<Void> unlockTemplate(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody UnlockTemplateRequest request) {
        pointsService.unlockTemplate(userId, request.getTemplateId());
        return ApiResponse.success(null, "模板解锁成功");
    }
}

// 请求类
class EarnPointsRequest {
    private String source;
    private String relatedId;
    private String description;
    
    // getters & setters
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRelatedId() { return relatedId; }
    public void setRelatedId(String relatedId) { this.relatedId = relatedId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

class SpendPointsRequest {
    private String source;
    private String relatedId;
    private String description;
    
    // getters & setters
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRelatedId() { return relatedId; }
    public void setRelatedId(String relatedId) { this.relatedId = relatedId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

class UnlockTemplateRequest {
    private String templateId;
    
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
}
