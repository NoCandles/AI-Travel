package com.ai.travel.admin.service;

import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.entity.AdminLog;
import com.ai.travel.admin.entity.AdminUser;
import com.ai.travel.admin.entity.view.AnnouncementView;
import com.ai.travel.admin.entity.view.SensitiveWordView;
import com.ai.travel.admin.repository.AdminLogRepository;
import com.ai.travel.admin.repository.AdminUserRepository;
import com.ai.travel.admin.repository.view.AnnouncementViewRepository;
import com.ai.travel.admin.repository.view.SensitiveWordViewRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSystemService {

    private final AdminUserRepository adminUserRepository;
    private final AdminLogRepository adminLogRepository;
    private final AnnouncementViewRepository announcementRepository;
    private final SensitiveWordViewRepository sensitiveWordRepository;
    private final PasswordEncoder passwordEncoder;

    // ===== 管理员管理 =====

    public List<AdminUser> listAdmins() {
        return adminUserRepository.selectList(
                new LambdaQueryWrapper<AdminUser>().orderByAsc(AdminUser::getCreatedAt));
    }

    public AdminUser createAdmin(AdminUser admin) {
        AdminUser exists = adminUserRepository.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, admin.getUsername()));
        if (exists != null) throw new RuntimeException("用户名已存在");
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        admin.setRole("admin");
        admin.setStatus(1);
        adminUserRepository.insert(admin);
        admin.setPassword(null);
        return admin;
    }

    public void updateAdmin(String id, AdminUser update) {
        AdminUser admin = adminUserRepository.selectById(id);
        if (admin == null) throw new RuntimeException("管理员不存在");
        if (update.getNickname() != null) admin.setNickname(update.getNickname());
        if (update.getRole() != null) admin.setRole(update.getRole());
        if (update.getStatus() != null) admin.setStatus(update.getStatus());
        if (update.getPassword() != null && !update.getPassword().isEmpty()) {
            admin.setPassword(passwordEncoder.encode(update.getPassword()));
        }
        adminUserRepository.updateById(admin);
    }

    // ===== 操作日志 =====

    public PageResult<AdminLog> listLogs(int page, int size, String module, String adminId) {
        LambdaQueryWrapper<AdminLog> w = new LambdaQueryWrapper<>();
        if (module != null && !module.isEmpty()) w.eq(AdminLog::getModule, module);
        if (adminId != null && !adminId.isEmpty()) w.eq(AdminLog::getAdminId, adminId);
        w.orderByDesc(AdminLog::getCreatedAt);
        return PageResult.of(adminLogRepository.selectPage(new Page<>(page, size), w));
    }

    // ===== 公告管理 =====

    public PageResult<AnnouncementView> listAnnouncements(int page, int size) {
        LambdaQueryWrapper<AnnouncementView> w = new LambdaQueryWrapper<>();
        w.orderByDesc(AnnouncementView::getCreatedAt);
        return PageResult.of(announcementRepository.selectPage(new Page<>(page, size), w));
    }

    public AnnouncementView createAnnouncement(AnnouncementView ann) {
        ann.setPublishAt(LocalDateTime.now());
        ann.setStatus(1);
        announcementRepository.insert(ann);
        return ann;
    }

    public void updateAnnouncement(String id, AnnouncementView ann) {
        ann.setId(id);
        announcementRepository.updateById(ann);
    }

    public void deleteAnnouncement(String id) {
        announcementRepository.deleteById(id);
    }

    // ===== 敏感词管理 =====

    public PageResult<SensitiveWordView> listSensitiveWords(int page, int size) {
        LambdaQueryWrapper<SensitiveWordView> w = new LambdaQueryWrapper<>();
        w.orderByDesc(SensitiveWordView::getCreatedAt);
        return PageResult.of(sensitiveWordRepository.selectPage(new Page<>(page, size), w));
    }

    public SensitiveWordView createSensitiveWord(SensitiveWordView word) {
        // 检查是否已存在
        Long exists = sensitiveWordRepository.selectCount(
                new LambdaQueryWrapper<SensitiveWordView>().eq(SensitiveWordView::getWord, word.getWord()));
        if (exists != null && exists > 0) {
            throw new RuntimeException("敏感词已存在");
        }
        word.setLevel(word.getLevel() == null ? 1 : word.getLevel());
        sensitiveWordRepository.insert(word);
        return word;
    }

    public void updateSensitiveWord(Long id, SensitiveWordView update) {
        SensitiveWordView word = sensitiveWordRepository.selectById(id);
        if (word == null) throw new RuntimeException("敏感词不存在");
        if (update.getWord() != null) word.setWord(update.getWord());
        if (update.getLevel() != null) word.setLevel(update.getLevel());
        sensitiveWordRepository.updateById(word);
    }

    public void deleteSensitiveWord(Long id) {
        sensitiveWordRepository.deleteById(id);
    }
}
