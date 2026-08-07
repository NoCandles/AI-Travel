package com.ai.travel.controller;

import com.ai.travel.entity.*;
import com.ai.travel.repository.LevelConfigRepository;
import com.ai.travel.config.UserContext;
import com.ai.travel.config.RequireLogin;
import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointsController {
    
    private final PointsService pointsService;
    private final LevelConfigRepository levelConfigRepository;
    
    @RequireLogin
    @GetMapping("/my")
    public ApiResponse<UserPoints> getMyPoints() {
        String userId = UserContext.getCurrentUserId();
        UserPoints points = pointsService.getUserPoints(userId);
        return ApiResponse.success(points);
    }
    
    @RequireLogin
    @PostMapping("/sign-in")
    public ApiResponse<Map<String, Object>> signIn() {
        String userId = UserContext.getCurrentUserId();
        Map<String, Object> result = pointsService.signIn(userId);
        return ApiResponse.success(result, "签到成功");
    }
    
    @RequireLogin
    @GetMapping("/records")
    public ApiResponse<List<PointRecord>> getRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = UserContext.getCurrentUserId();
        List<PointRecord> records = pointsService.getPointRecords(userId, page, size);
        return ApiResponse.success(records);
    }
    
    @RequireLogin
    @PostMapping("/earn")
    public ApiResponse<Void> earnPoints(@RequestBody EarnPointsRequest request) {
        String userId = UserContext.getCurrentUserId();
        pointsService.earnPoints(userId, request.getSource(), 
            request.getRelatedId(), request.getDescription());
        return ApiResponse.success(null, "积分已添加");
    }
    
    @RequireLogin
    @PostMapping("/spend")
    public ApiResponse<Void> spendPoints(@RequestBody SpendPointsRequest request) {
        String userId = UserContext.getCurrentUserId();
        pointsService.spendPoints(userId, request.getSource(), 
            request.getRelatedId(), request.getDescription());
        return ApiResponse.success(null, "积分已扣除");
    }
    
    @GetMapping("/level-config")
    public ApiResponse<List<LevelConfig>> getLevelConfig() {
        List<LevelConfig> levels = levelConfigRepository.selectList(null);
        return ApiResponse.success(levels);
    }
    
    @RequireLogin
    @PostMapping("/unlock-template")
    public ApiResponse<Void> unlockTemplate(@RequestBody UnlockTemplateRequest request) {
        String userId = UserContext.getCurrentUserId();
        pointsService.unlockTemplate(userId, request.getTemplateId());
        return ApiResponse.success(null, "模板解锁成功");
    }
}

class EarnPointsRequest {
    private String source;
    private String relatedId;
    private String description;
    
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
