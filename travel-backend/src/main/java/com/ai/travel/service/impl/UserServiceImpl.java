package com.ai.travel.service.impl;

import com.ai.travel.dto.UserProfileDTO;
import com.ai.travel.entity.*;
import com.ai.travel.repository.*;
import com.ai.travel.service.PointsService;
import com.ai.travel.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TripPublishRepository tripPublishRepository;
    private final FollowRelationRepository followRelationRepository;
    private final LikeRecordRepository likeRecordRepository;
    private final PointsService pointsService;

    @Override
    public UserProfileDTO getUserProfile(String userId) {
        User user = userRepository.selectById(userId);
        
        if (user == null) {
            log.warn("用户不存在：userId={}", userId);
            return null;
        }
        
        return convertToDTO(user);
    }

    @Override
    @Transactional
    public UserProfileDTO updateUserProfile(String userId, UserProfileDTO userProfileDTO) {
        User user = userRepository.selectById(userId);
        
        if (user == null) {
            log.warn("用户不存在，创建新用户：userId={}", userId);
            user = new User();
            user.setId(userId);
        }
        
        if (userProfileDTO.getNickname() != null) {
            user.setNickname(userProfileDTO.getNickname());
        }
        if (userProfileDTO.getAvatar() != null) {
            user.setAvatar(userProfileDTO.getAvatar());
        }
        if (userProfileDTO.getGender() != null) {
            user.setGender(userProfileDTO.getGender());
        }
        if (userProfileDTO.getCity() != null) {
            user.setCity(userProfileDTO.getCity());
        }
        if (userProfileDTO.getSignature() != null) {
            user.setSignature(userProfileDTO.getSignature());
        }
        if (userProfileDTO.getCountry() != null) {
            user.setCountry(userProfileDTO.getCountry());
        }
        if (userProfileDTO.getProvince() != null) {
            user.setProvince(userProfileDTO.getProvince());
        }
        
        if (user.getId() != null) {
            userRepository.updateById(user);
        } else {
            userRepository.insert(user);
        }
        
        log.info("用户资料更新成功：userId={}", userId);
        return convertToDTO(user);
    }

    private UserProfileDTO convertToDTO(User user) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        dto.setGender(user.getGender());
        dto.setCity(user.getCity());
        dto.setSignature(user.getSignature());
        dto.setCountry(user.getCountry());
        dto.setProvince(user.getProvince());
        return dto;
    }

    @Override
    public Map<String, Object> getUserStats(String userId) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 1. 获取行程数量（用户创建的行程）
            QueryWrapper<TripPlan> tripQuery = new QueryWrapper<>();
            tripQuery.eq("user_id", userId);
            Long tripCount = tripPlanRepository.selectCount(tripQuery);
            stats.put("tripCount", tripCount != null ? tripCount : 0);
            
            // 2. 获取发布数量（用户发布到广场的行程）
            QueryWrapper<TripPublish> publishQuery = new QueryWrapper<>();
            publishQuery.eq("user_id", userId);
            Long publishCount = tripPublishRepository.selectCount(publishQuery);
            stats.put("publishCount", publishCount != null ? publishCount : 0);
            
            // 3. 获取收藏数量（从 like_record 表查 action_type='fav'）
            QueryWrapper<LikeRecord> favoriteQuery = new QueryWrapper<>();
            favoriteQuery.eq("user_id", userId);
            favoriteQuery.eq("action_type", "fav");
            Long favoriteCount = likeRecordRepository.selectCount(favoriteQuery);
            stats.put("favoriteCount", favoriteCount != null ? favoriteCount : 0);
            
            // 4. 获取关注数量（排除自己）
            QueryWrapper<FollowRelation> followQuery = new QueryWrapper<>();
            followQuery.eq("follower_id", userId);
            followQuery.ne("following_id", userId);
            Long followingCount = followRelationRepository.selectCount(followQuery);
            stats.put("followingCount", followingCount != null ? followingCount : 0);
            
            // 5. 获取积分信息
            try {
                UserPoints userPoints = pointsService.getUserPoints(userId);
                stats.put("availablePoints", userPoints.getAvailablePoints());
                stats.put("totalPoints", userPoints.getTotalPoints());
                stats.put("usedPoints", userPoints.getUsedPoints());
                stats.put("level", userPoints.getLevel());
                stats.put("signInStreak", userPoints.getSignInStreak());
                
                // 计算等级称号
                String title = getLevelTitle(userPoints.getLevel());
                stats.put("levelTitle", title);
            } catch (Exception e) {
                log.warn("获取积分信息失败：userId={}", userId, e);
                stats.put("availablePoints", 0);
                stats.put("totalPoints", 0);
                stats.put("usedPoints", 0);
                stats.put("level", 1);
                stats.put("levelTitle", "旅游新手");
                stats.put("signInStreak", 0);
            }
            
            log.info("获取用户统计数据成功：userId={}, stats={}", userId, stats);
            
        } catch (Exception e) {
            log.error("获取用户统计数据失败：userId={}", userId, e);
            stats.put("tripCount", 0);
            stats.put("publishCount", 0);
            stats.put("favoriteCount", 0);
            stats.put("followingCount", 0);
            stats.put("availablePoints", 0);
            stats.put("totalPoints", 0);
            stats.put("usedPoints", 0);
            stats.put("level", 1);
            stats.put("levelTitle", "旅游新手");
            stats.put("signInStreak", 0);
        }
        
        return stats;
    }

    private String getLevelTitle(int level) {
        switch (level) {
            case 5: return "拾路传说";
            case 4: return "旅行大师";
            case 3: return "旅行达人";
            case 2: return "旅行爱好者";
            default: return "旅游新手";
        }
    }

    @Override
    public String getPreferences(String userId) {
        User user = userRepository.selectById(userId);
        if (user == null) return "{}";
        return user.getPreferences() != null ? user.getPreferences() : "{}";
    }

    @Override
    @Transactional
    public void savePreferences(String userId, String preferencesJson) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            user = new User();
            user.setId(userId);
            user.setPreferences(preferencesJson);
            userRepository.insert(user);
        } else {
            user.setPreferences(preferencesJson);
            userRepository.updateById(user);
        }
        log.info("用户偏好设置保存成功：userId={}", userId);
    }

    @Override
    public List<String> getFootprint(String userId) {
        return tripPlanRepository.findCompletedDestinationsByUserId(userId);
    }

    @Override
    public List<Map<String, Object>> getFootprintDetail(String userId) {
        return tripPlanRepository.countCompletedTripsByDestination(userId);
    }
}
