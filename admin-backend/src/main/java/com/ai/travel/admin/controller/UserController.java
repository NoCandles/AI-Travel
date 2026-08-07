package com.ai.travel.admin.controller;

import com.ai.travel.admin.config.AdminLog;
import com.ai.travel.admin.dto.ApiResponse;
import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.dto.UserDetailDTO;
import com.ai.travel.admin.entity.view.UserView;
import com.ai.travel.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final AdminUserService adminUserService;

    /**
     * 用户分页列表
     */
    @GetMapping
    public ApiResponse<PageResult<UserView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(adminUserService.listUsers(page, size, keyword, status));
    }

    /**
     * 用户详情
     */
    @GetMapping("/{id}")
    public ApiResponse<UserDetailDTO> detail(@PathVariable String id) {
        return ApiResponse.success(adminUserService.getUserDetail(id));
    }

    /**
     * 封禁/解封用户
     */
    @AdminLog(module = "user", action = "ban", targetType = "user")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        adminUserService.updateUserStatus(id, status);
        String msg = status == 1 ? "解封成功" : "封禁成功";
        return ApiResponse.success(null, msg);
    }
}
