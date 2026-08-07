package com.ai.travel.admin.service;

import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.entity.view.*;
import com.ai.travel.admin.repository.view.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPointsService {

    private final PointRecordViewRepository recordRepository;
    private final UserPointsViewRepository userPointsRepository;
    private final SignInRecordViewRepository signInRepository;
    private final LevelConfigViewRepository levelConfigRepository;

    public PageResult<PointRecordView> listRecords(int page, int size, String userId, String type, String source) {
        LambdaQueryWrapper<PointRecordView> w = new LambdaQueryWrapper<>();
        if (userId != null && !userId.isEmpty()) w.eq(PointRecordView::getUserId, userId);
        if (type != null && !type.isEmpty()) w.eq(PointRecordView::getType, type);
        if (source != null && !source.isEmpty()) w.eq(PointRecordView::getSource, source);
        w.orderByDesc(PointRecordView::getCreatedAt);
        return PageResult.of(recordRepository.selectPage(new Page<>(page, size), w));
    }

    public List<LevelConfigView> getLevelConfig() {
        return levelConfigRepository.selectList(
                new LambdaQueryWrapper<LevelConfigView>().orderByAsc(LevelConfigView::getLevel));
    }

    public void updateLevelConfig(Long id, LevelConfigView config) {
        config.setId(id);
        levelConfigRepository.updateById(config);
    }

    public void manualPoints(String userId, int points, String reason) {
        UserPointsView up = userPointsRepository.selectOne(
                new LambdaQueryWrapper<UserPointsView>().eq(UserPointsView::getUserId, userId));
        if (up == null) throw new RuntimeException("用户积分记录不存在");

        up.setAvailablePoints(Math.max(0, up.getAvailablePoints() + points));
        up.setTotalPoints(Math.max(0, up.getTotalPoints() + points));
        userPointsRepository.updateById(up);
    }

    public PageResult<SignInRecordView> listSignInLog(int page, int size, String userId) {
        LambdaQueryWrapper<SignInRecordView> w = new LambdaQueryWrapper<>();
        if (userId != null && !userId.isEmpty()) w.eq(SignInRecordView::getUserId, userId);
        w.orderByDesc(SignInRecordView::getSignInDate);
        return PageResult.of(signInRepository.selectPage(new Page<>(page, size), w));
    }
}
