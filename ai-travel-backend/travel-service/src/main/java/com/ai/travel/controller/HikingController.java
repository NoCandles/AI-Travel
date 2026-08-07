package com.ai.travel.controller;

import com.ai.travel.common.dto.ApiResponse;
import com.ai.travel.config.RequireLogin;
import com.ai.travel.entity.HikingRoute;
import com.ai.travel.entity.HikingSegment;
import com.ai.travel.repository.HikingRouteRepository;
import com.ai.travel.repository.HikingSegmentRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 徒步路线 API
 */
@RestController
@RequestMapping("/api/v1/hiking")
@RequiredArgsConstructor
public class HikingController {

    private final HikingRouteRepository hikingRouteRepository;
    private final HikingSegmentRepository hikingSegmentRepository;

    /**
     * 分页查询公开的徒步路线（路线广场，
     * 支持筛选：难度、路线类型、里程范围、是否有露营地、是否亲子友好
     */
    @GetMapping("/routes")
    public ApiResponse<IPage<HikingRoute>> getPublicRoutes(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String routeType,
            @RequestParam(required = false) String distanceRange,
            @RequestParam(required = false) Boolean hasCampsite,
            @RequestParam(required = false) Boolean isFamilyFriendly,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<HikingRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HikingRoute::getIsPublic, 1);

        // 难度筛选
        if (difficulty != null) {
            wrapper.eq(HikingRoute::getDifficulty, difficulty);
        }

        // 路线类型筛选
        if (routeType != null && !routeType.isEmpty()) {
            wrapper.eq(HikingRoute::getRouteType, routeType);
        }

        // 里程范围筛选
        if (distanceRange != null && !distanceRange.isEmpty()) {
            applyDistanceFilter(wrapper, distanceRange);
        }

        // 露营在
        if (hasCampsite != null && hasCampsite) {
            wrapper.eq(HikingRoute::getHasCampsite, 1);
        }

        // 亲子友好
        if (isFamilyFriendly != null && isFamilyFriendly) {
            wrapper.eq(HikingRoute::getIsFamilyFriendly, 1);
        }

        // 关键词搜索
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(HikingRoute::getRouteName, keyword);
        }

        wrapper.orderByDesc(HikingRoute::getCreatedAt);

        IPage<HikingRoute> result = hikingRouteRepository.selectPage(new Page<>(page, size), wrapper);
        return ApiResponse.success(result);
    }

    /**
     * 获取单条徒步路线详情
     */
    @GetMapping("/routes/{id}")
    public ApiResponse<HikingRoute> getRouteDetail(@PathVariable String id) {
        HikingRoute route = hikingRouteRepository.selectById(id);
        if (route == null) {
            return ApiResponse.error("路线不存在");
        }
        // 浏览量+1（异步更新，不阻塞响应）
        hikingRouteRepository.incrementViewCount(id);
        return ApiResponse.success(route);
    }

    /**
     * 获取某条路线的所有路段
     */
    @GetMapping("/routes/{id}/segments")
    public ApiResponse<List<HikingSegment>> getRouteSegments(@PathVariable String id) {
        LambdaQueryWrapper<HikingSegment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HikingSegment::getHikingRouteId, id)
               .orderByAsc(HikingSegment::getDayNum)
               .orderByAsc(HikingSegment::getOrderNum);
        List<HikingSegment> segments = hikingSegmentRepository.selectList(wrapper);
        return ApiResponse.success(segments);
    }

    /**
     * 获取某条路线的安全指单
     */
    @GetMapping("/routes/{id}/safety")
    public ApiResponse<String> getRouteSafety(@PathVariable String id) {
        HikingRoute route = hikingRouteRepository.selectById(id);
        if (route == null) {
            return ApiResponse.error("路线不存在");
        }
        return ApiResponse.success(route.getSafetyJson());
    }

    /**
     * 获取某条路线的装备建议
     */
    @GetMapping("/routes/{id}/gear")
    public ApiResponse<String> getRouteGear(@PathVariable String id) {
        HikingRoute route = hikingRouteRepository.selectById(id);
        if (route == null) {
            return ApiResponse.error("路线不存在");
        }
        return ApiResponse.success(route.getGearJson());
    }

    /**
     * 发布徒步路线到广在
     */
    @RequireLogin
    @PostMapping("/routes/{id}/publish")
    public ApiResponse<Void> publishRoute(@PathVariable String id) {
        HikingRoute route = hikingRouteRepository.selectById(id);
        if (route == null) {
            return ApiResponse.error("路线不存在");
        }
        route.setIsPublic(1);
        hikingRouteRepository.updateById(route);
        return ApiResponse.success(null, "路线已发布到广场");
    }

    /**
     * 按行程ID 获取关联的徒步路线
     */
    @GetMapping("/trip/{tripPlanId}")
    public ApiResponse<List<HikingRoute>> getRoutesByTripPlan(@PathVariable String tripPlanId) {
        LambdaQueryWrapper<HikingRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HikingRoute::getTripPlanId, tripPlanId)
               .orderByAsc(HikingRoute::getCreatedAt);
        List<HikingRoute> routes = hikingRouteRepository.selectList(wrapper);
        return ApiResponse.success(routes);
    }

    // ==================== 辅助方法 ====================

    /**
     * 里程范围筛选映射
     */
    private void applyDistanceFilter(LambdaQueryWrapper<HikingRoute> wrapper, String distanceRange) {
        switch (distanceRange) {
            case "1-3km":
                wrapper.ge(HikingRoute::getTotalDistance, new java.math.BigDecimal("1"))
                       .le(HikingRoute::getTotalDistance, new java.math.BigDecimal("3"));
                break;
            case "3-8km":
                wrapper.ge(HikingRoute::getTotalDistance, new java.math.BigDecimal("3"))
                       .le(HikingRoute::getTotalDistance, new java.math.BigDecimal("8"));
                break;
            case "8-15km":
                wrapper.ge(HikingRoute::getTotalDistance, new java.math.BigDecimal("8"))
                       .le(HikingRoute::getTotalDistance, new java.math.BigDecimal("15"));
                break;
            case "15km+":
                wrapper.gt(HikingRoute::getTotalDistance, new java.math.BigDecimal("15"));
                break;
            default:
                break;
        }
    }
}
