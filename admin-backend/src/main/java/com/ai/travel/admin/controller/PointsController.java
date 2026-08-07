package com.ai.travel.admin.controller;

import com.ai.travel.admin.config.AdminLog;
import com.ai.travel.admin.dto.ApiResponse;
import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.entity.view.LevelConfigView;
import com.ai.travel.admin.entity.view.PointRecordView;
import com.ai.travel.admin.entity.view.SignInRecordView;
import com.ai.travel.admin.service.AdminPointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/points")
@RequiredArgsConstructor
public class PointsController {

    private final AdminPointsService pointsService;

    @GetMapping("/records")
    public ApiResponse<PageResult<PointRecordView>> listRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String source) {
        return ApiResponse.success(pointsService.listRecords(page, size, userId, type, source));
    }

    @GetMapping("/config")
    public ApiResponse<List<LevelConfigView>> getConfig() {
        return ApiResponse.success(pointsService.getLevelConfig());
    }

    @AdminLog(module = "points", action = "update", targetType = "level_config")
    @PutMapping("/config/{id}")
    public ApiResponse<Void> updateConfig(@PathVariable Long id, @RequestBody LevelConfigView config) {
        pointsService.updateLevelConfig(id, config);
        return ApiResponse.success(null, "更新成功");
    }

    @AdminLog(module = "points", action = "update", targetType = "points")
    @PostMapping("/manual")
    public ApiResponse<Void> manualPoints(
            @RequestParam String userId,
            @RequestParam int points,
            @RequestParam String reason) {
        pointsService.manualPoints(userId, points, reason);
        return ApiResponse.success(null, "操作成功");
    }

    @GetMapping("/sign-log")
    public ApiResponse<PageResult<SignInRecordView>> signLog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId) {
        return ApiResponse.success(pointsService.listSignInLog(page, size, userId));
    }
}
