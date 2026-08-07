package com.ai.travel.service;

import com.ai.travel.entity.*;
import com.ai.travel.repository.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsService {
    
    private final UserPointsRepository userPointsRepository;
    private final PointRecordRepository pointRecordRepository;
    private final SignInRecordRepository signInRecordRepository;
    private final LevelConfigRepository levelConfigRepository;
    private final UserUnlockedTemplateRepository userUnlockedTemplateRepository;
    
    /**
     * 获取用户积分信息
     */
    public UserPoints getUserPoints(String userId) {
        LambdaQueryWrapper<UserPoints> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPoints::getUserId, userId);
        UserPoints userPoints = userPointsRepository.selectOne(wrapper);
        
        if (userPoints == null) {
            // 新用户初始化积分账户
            userPoints = new UserPoints();
            userPoints.setUserId(userId);
            userPoints.setTotalPoints(0);
            userPoints.setAvailablePoints(0);
            userPoints.setUsedPoints(0);
            userPoints.setLevel(1);
            userPoints.setSignInStreak(0);
            userPoints.setCreatedAt(LocalDateTime.now());
            userPoints.setUpdatedAt(LocalDateTime.now());
            userPointsRepository.insert(userPoints);
        }
        return userPoints;
    }
    
    /**
     * 每日签到
     */
    @Transactional
    public Map<String, Object> signIn(String userId) {
        LocalDate today = LocalDate.now();
        
        // 检查今天是否已签到
        LambdaQueryWrapper<SignInRecord> signInWrapper = new LambdaQueryWrapper<>();
        signInWrapper.eq(SignInRecord::getUserId, userId)
                   .eq(SignInRecord::getSignInDate, today);
        SignInRecord existingSignIn = signInRecordRepository.selectOne(signInWrapper);
        
        if (existingSignIn != null) {
            throw new RuntimeException("今日已签到");
        }
        
        // 获取用户积分账户
        UserPoints userPoints = getUserPoints(userId);
        
        // 计算连续签到天数
        int streak = userPoints.getSignInStreak();
        LocalDate lastDate = userPoints.getLastSignInDate();
        if (lastDate != null && lastDate.plusDays(1).equals(today)) {
            streak++;
        } else {
            streak = 1;
        }
        
        // 计算签到积分(连续签到奖励)
        int pointsEarned = 5;
        if (streak >= 7) pointsEarned = 15; // 连续7天+ 额外奖励
        else if (streak >= 3) pointsEarned = 10; // 连续3天+ 额外奖励
        
        // 更新积分账户
        userPoints.setTotalPoints(userPoints.getTotalPoints() + pointsEarned);
        userPoints.setAvailablePoints(userPoints.getAvailablePoints() + pointsEarned);
        userPoints.setSignInStreak(streak);
        userPoints.setLastSignInDate(today);
        userPoints.setUpdatedAt(LocalDateTime.now());
        userPointsRepository.updateById(userPoints);
        
        // 记录签到
        SignInRecord signIn = new SignInRecord();
        signIn.setUserId(userId);
        signIn.setSignInDate(today);
        signIn.setPointsEarned(pointsEarned);
        signIn.setCreatedAt(LocalDateTime.now());
        signInRecordRepository.insert(signIn);
        
        // 记录积分变动
        recordPointChange(userId, pointsEarned, userPoints.getAvailablePoints(), 
            "earn", "sign_in", null, "每日签到奖励");
        
        // 更新等级
        updateUserLevel(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("pointsEarned", pointsEarned);
        result.put("streak", streak);
        result.put("availablePoints", userPoints.getAvailablePoints());
        return result;
    }
    
    /**
     * 赚取积分
     */
    @Transactional
    public void earnPoints(String userId, String source, String relatedId, String description) {
        // 检查每日限制
        if (!checkDailyLimit(userId, source)) {
            log.info("用户{}今日{}次数已达上限", userId, source);
            return;
        }
        
        int points = getPointsBySource(source);
        if (points <= 0) return;
        
        UserPoints userPoints = getUserPoints(userId);
        userPoints.setTotalPoints(userPoints.getTotalPoints() + points);
        userPoints.setAvailablePoints(userPoints.getAvailablePoints() + points);
        userPoints.setUpdatedAt(LocalDateTime.now());
        userPointsRepository.updateById(userPoints);
        
        recordPointChange(userId, points, userPoints.getAvailablePoints(), 
            "earn", source, relatedId, description);
        
        // 更新等级
        updateUserLevel(userId);
    }
    
    /**
     * 消费积分
     */
    @Transactional
    public void spendPoints(String userId, String source, String relatedId, String description) {
        int points = Math.abs(getPointsBySource(source)); // 获取绝对值
        if (points <= 0) return;
        
        UserPoints userPoints = getUserPoints(userId);
        if (userPoints.getAvailablePoints() < points) {
            throw new RuntimeException("积分不足");
        }
        
        userPoints.setAvailablePoints(userPoints.getAvailablePoints() - points);
        userPoints.setUsedPoints(userPoints.getUsedPoints() + points);
        userPoints.setUpdatedAt(LocalDateTime.now());
        userPointsRepository.updateById(userPoints);
        
        recordPointChange(userId, -points, userPoints.getAvailablePoints(), 
            "spend", source, relatedId, description);
    }
    
    /**
     * 记录积分变动
     */
    private void recordPointChange(String userId, int changeValue, int currentBalance, 
            String type, String source, String relatedId, String description) {
        PointRecord record = new PointRecord();
        record.setUserId(userId);
        record.setChangeValue(changeValue);
        record.setCurrentBalance(currentBalance);
        record.setType(type);
        record.setSource(source);
        record.setRelatedId(relatedId);
        record.setDescription(description);
        record.setCreatedAt(LocalDateTime.now());
        pointRecordRepository.insert(record);
    }
    
    /**
     * 根据来源获取积分值
     */
    private int getPointsBySource(String source) {
        switch (source) {
            case "sign_in": return 5;
            case "publish": return 20;
            case "review": return 10;
            case "receive_like": return 2;
            case "comment": return 3;
            case "follow": return 1;
            case "complete_profile": return 30;
            case "first_share": return 15;
            case "ai_plan": return -5;
            case "unlock_template": return -50;
            default: return 0;
        }
    }
    
    /**
     * 检查每日限制
     */
    private boolean checkDailyLimit(String userId, String source) {
        LocalDate today = LocalDate.now();
        
        LambdaQueryWrapper<PointRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointRecord::getUserId, userId)
               .eq(PointRecord::getSource, source)
               .apply("DATE(created_at) = {0}", today);
        
        long count = pointRecordRepository.selectCount(wrapper);
        
        switch (source) {
            case "publish":
                return count < 3;
            case "review":
                return count < 5;
            case "receive_like":
                return count < 50;
            case "comment":
                return count < 10;
            default:
                return true;
        }
    }
    
    /**
     * 更新用户等级
     */
    private void updateUserLevel(String userId) {
        UserPoints userPoints = getUserPoints(userId);
        int currentPoints = userPoints.getTotalPoints();
        
        // 查询对应的等级
        LambdaQueryWrapper<LevelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(LevelConfig::getMinPoints, currentPoints)
               .ge(LevelConfig::getMaxPoints, currentPoints);
        LevelConfig levelConfig = levelConfigRepository.selectOne(wrapper);
        
        if (levelConfig != null && levelConfig.getLevel() != userPoints.getLevel()) {
            userPoints.setLevel(levelConfig.getLevel());
            userPoints.setUpdatedAt(LocalDateTime.now());
            userPointsRepository.updateById(userPoints);
            log.info("用户{}等级提升到L{}", userId, levelConfig.getLevel());
        }
    }
    
    /**
     * 获取积分变动记录
     */
    public List<PointRecord> getPointRecords(String userId, int page, int size) {
        LambdaQueryWrapper<PointRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointRecord::getUserId, userId)
               .orderByDesc(PointRecord::getCreatedAt);
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointRecord> pageRequest = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        
        return pointRecordRepository.selectPage(pageRequest, wrapper).getRecords();
    }
    
    /**
     * 检查用户是否已解锁模板
     */
    public boolean isTemplateUnlocked(String userId, String templateId) {
        LambdaQueryWrapper<UserUnlockedTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserUnlockedTemplate::getUserId, userId)
               .eq(UserUnlockedTemplate::getTemplateId, templateId);
        return userUnlockedTemplateRepository.selectOne(wrapper) != null;
    }
    
    /**
     * 解锁模板
     */
    @Transactional
    public void unlockTemplate(String userId, String templateId) {
        // 检查是否已解锁
        if (isTemplateUnlocked(userId, templateId)) {
            throw new RuntimeException("模板已解锁");
        }
        
        // 检查用户等级是否满足免费解锁条件
        UserPoints userPoints = getUserPoints(userId);
        if (userPoints.getLevel() >= 5) {
            // L5及以上免费解锁
            UserUnlockedTemplate unlock = new UserUnlockedTemplate();
            unlock.setUserId(userId);
            unlock.setTemplateId(templateId);
            unlock.setUnlockedAt(LocalDateTime.now());
            userUnlockedTemplateRepository.insert(unlock);
            return;
        }
        
        // 消费积分解锁
        spendPoints(userId, "unlock_template", templateId, "解锁高级模板");
        
        UserUnlockedTemplate unlock = new UserUnlockedTemplate();
        unlock.setUserId(userId);
        unlock.setTemplateId(templateId);
        unlock.setUnlockedAt(LocalDateTime.now());
        userUnlockedTemplateRepository.insert(unlock);
    }
    
    /**
     * 计算AI规划所需积分(考虑等级折扣)
     */
    public int calculateAICost(String userId) {
        UserPoints userPoints = getUserPoints(userId);
        int baseCost = 5;
        
        if (userPoints.getLevel() >= 2) {
            // L2+ AI规划9折
            return (int)(baseCost * 0.9);
        }
        
        return baseCost;
    }
}
