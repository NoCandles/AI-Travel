package com.ai.travel.admin.controller;

import com.ai.travel.admin.dto.ApiResponse;
import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.entity.AdminLog;
import com.ai.travel.admin.entity.AdminUser;
import com.ai.travel.admin.entity.view.AnnouncementView;
import com.ai.travel.admin.entity.view.SensitiveWordView;
import com.ai.travel.admin.service.AdminSystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class SystemController {

    private final AdminSystemService systemService;

    // ===== 管理员管理 =====

    @GetMapping("/admins")
    public ApiResponse<List<AdminUser>> listAdmins() {
        List<AdminUser> list = systemService.listAdmins();
        list.forEach(a -> a.setPassword(null));
        return ApiResponse.success(list);
    }

    @com.ai.travel.admin.config.AdminLog(module = "system", action = "create", targetType = "admin")
    @PostMapping("/admins")
    public ApiResponse<AdminUser> createAdmin(@RequestBody AdminUser admin) {
        return ApiResponse.success(systemService.createAdmin(admin), "创建成功");
    }

    @com.ai.travel.admin.config.AdminLog(module = "system", action = "update", targetType = "admin")
    @PutMapping("/admins/{id}")
    public ApiResponse<Void> updateAdmin(@PathVariable String id, @RequestBody AdminUser admin) {
        systemService.updateAdmin(id, admin);
        return ApiResponse.success(null, "更新成功");
    }

    // ===== 操作日志 =====

    @GetMapping("/logs")
    public ApiResponse<PageResult<AdminLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String adminId) {
        return ApiResponse.success(systemService.listLogs(page, size, module, adminId));
    }

    // ===== 公告管理 =====

    @GetMapping("/announcements")
    public ApiResponse<PageResult<AnnouncementView>> listAnnouncements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(systemService.listAnnouncements(page, size));
    }

    @com.ai.travel.admin.config.AdminLog(module = "system", action = "create", targetType = "announcement")
    @PostMapping("/announcements")
    public ApiResponse<AnnouncementView> createAnnouncement(@RequestBody AnnouncementView ann) {
        return ApiResponse.success(systemService.createAnnouncement(ann), "发布成功");
    }

    @com.ai.travel.admin.config.AdminLog(module = "system", action = "update", targetType = "announcement")
    @PutMapping("/announcements/{id}")
    public ApiResponse<Void> updateAnnouncement(@PathVariable String id, @RequestBody AnnouncementView ann) {
        systemService.updateAnnouncement(id, ann);
        return ApiResponse.success(null, "更新成功");
    }

    @com.ai.travel.admin.config.AdminLog(module = "system", action = "delete", targetType = "announcement")
    @DeleteMapping("/announcements/{id}")
    public ApiResponse<Void> deleteAnnouncement(@PathVariable String id) {
        systemService.deleteAnnouncement(id);
        return ApiResponse.success(null, "删除成功");
    }

    // ===== 敏感词管理 =====

    @GetMapping("/sensitive-words")
    public ApiResponse<PageResult<SensitiveWordView>> listSensitiveWords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(systemService.listSensitiveWords(page, size));
    }

    @com.ai.travel.admin.config.AdminLog(module = "system", action = "create", targetType = "sensitive_word")
    @PostMapping("/sensitive-words")
    public ApiResponse<SensitiveWordView> createSensitiveWord(@RequestBody SensitiveWordView word) {
        return ApiResponse.success(systemService.createSensitiveWord(word), "添加成功");
    }

    @com.ai.travel.admin.config.AdminLog(module = "system", action = "update", targetType = "sensitive_word")
    @PutMapping("/sensitive-words/{id}")
    public ApiResponse<Void> updateSensitiveWord(@PathVariable Long id, @RequestBody SensitiveWordView word) {
        systemService.updateSensitiveWord(id, word);
        return ApiResponse.success(null, "更新成功");
    }

    @com.ai.travel.admin.config.AdminLog(module = "system", action = "delete", targetType = "sensitive_word")
    @DeleteMapping("/sensitive-words/{id}")
    public ApiResponse<Void> deleteSensitiveWord(@PathVariable Long id) {
        systemService.deleteSensitiveWord(id);
        return ApiResponse.success(null, "删除成功");
    }
}
