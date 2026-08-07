package com.ai.travel.service.impl;

import com.ai.travel.common.exception.BusinessException;
import com.ai.travel.dto.UserProfileDTO;
import com.ai.travel.entity.*;
import com.ai.travel.repository.*;
import com.ai.travel.service.PointsService;
import com.ai.travel.service.SensitiveWordService;
import com.ai.travel.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final SensitiveWordService sensitiveWordService;

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
        boolean isNewUser = false;
        
        if (user == null) {
            log.warn("用户不存在，创建新用户：userId={}", userId);
            user = new User();
            user.setId(userId);
            isNewUser = true;
        }
        
        if (userProfileDTO.getNickname() != null) {
            String nickname = userProfileDTO.getNickname().trim();
            validateNickname(userId, nickname);
            user.setNickname(nickname);
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
        
        if (isNewUser) {
            userRepository.insert(user);
        } else {
            userRepository.updateById(user);
        }
        
        log.info("用户资料更新成功：userId={}", userId);
        return convertToDTO(user);
    }

    private void validateNickname(String userId, String nickname) {
        if (nickname.isEmpty()) {
            throw new BusinessException("请输入昵称");
        }

        String matchedWord = sensitiveWordService.checkText(nickname);
        if (matchedWord != null) {
            throw new BusinessException("昵称包含敏感词，请修改后再保存");
        }

        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("nickname", nickname)
                .ne("id", userId)
                .last("LIMIT 1");
        User exists = userRepository.selectOne(query);
        if (exists != null) {
            throw new BusinessException("昵称已被使用，请换一个昵称");
        }
    }

    private UserProfileDTO convertToDTO(User user) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        dto.setCoverImage(user.getCoverImage());
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
            // 1. 获取行程数量（只统计有效状态：SAVED、ONGOING、COMPLETED）
            QueryWrapper<TripPlan> tripQuery = new QueryWrapper<>();
            tripQuery.eq("user_id", userId);
            tripQuery.in("status", "SAVED", "ONGOING", "COMPLETED");
            Long tripCount = tripPlanRepository.selectCount(tripQuery);
            stats.put("tripCount", tripCount != null ? tripCount : 0);

            // 2. 获取发布数量（只统计已发布的，status=1）
            QueryWrapper<TripPublish> publishQuery = new QueryWrapper<>();
            publishQuery.eq("user_id", userId);
            publishQuery.eq("status", 1);
            Long publishCount = tripPublishRepository.selectCount(publishQuery);
            stats.put("publishCount", publishCount != null ? publishCount : 0);
            
            // 3. 获取收藏数量（从 like_record 表查 action_type='fav'）
            QueryWrapper<LikeRecord> favoriteQuery = new QueryWrapper<>();
            favoriteQuery.eq("user_id", userId);
            favoriteQuery.eq("action_type", "fav");
            Long favoriteCount = likeRecordRepository.selectCount(favoriteQuery);
            stats.put("favoriteCount", favoriteCount != null ? favoriteCount : 0);
            
            // 4. 获取关注数量（排除自己）
            Long followingCount = followRelationRepository.countValidFollowing(userId);
            stats.put("followingCount", followingCount != null ? followingCount : 0);

            // 5. 获取粉丝数量
            Long followerCount = followRelationRepository.countFollowers(userId);
            stats.put("followerCount", followerCount != null ? followerCount : 0);

            // 6. 获取获赞总数（所有发布的行程被点赞数之和）
            QueryWrapper<TripPublish> likesQuery = new QueryWrapper<>();
            likesQuery.eq("user_id", userId);
            likesQuery.select("IFNULL(SUM(like_count), 0) AS total_likes");
            Map<String, Object> likesResult = tripPublishRepository.selectMaps(likesQuery).stream().findFirst().orElse(null);
            long totalLikes = likesResult != null ? ((Number) likesResult.get("total_likes")).longValue() : 0;
            stats.put("totalLikes", totalLikes);

            // 7. 获取积分信息
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
            stats.put("followerCount", 0);
            stats.put("totalLikes", 0);
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
    public String getSettings(String userId) {
        User user = userRepository.selectById(userId);
        if (user == null) return "{}";
        return user.getSettings() != null ? user.getSettings() : "{}";
    }

    @Override
    @Transactional
    public void saveSettings(String userId, String settingsJson) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            user = new User();
            user.setId(userId);
            user.setSettings(settingsJson);
            userRepository.insert(user);
        } else {
            user.setSettings(settingsJson);
            userRepository.updateById(user);
        }
        log.info("用户系统设置保存成功：userId={}", userId);
    }

    @Override
    public List<String> getFootprint(String userId) {
        return tripPlanRepository.findCompletedDestinationsByUserId(userId);
    }

    @Override
    public List<Map<String, Object>> getFootprintDetail(String userId) {
        return mergeFootprintDestinations(tripPlanRepository.countCompletedTripsByDestination(userId));
    }

    private List<Map<String, Object>> mergeFootprintDestinations(List<Map<String, Object>> rows) {
        Map<String, Integer> countMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object destinationValue = row.get("destination");
            if (destinationValue == null) {
                continue;
            }
            String destination = destinationValue.toString().trim();
            if (destination.isEmpty()) {
                continue;
            }
            int tripCount = 1;
            Object tripCountValue = row.get("tripCount");
            if (tripCountValue instanceof Number) {
                tripCount = ((Number) tripCountValue).intValue();
            } else if (tripCountValue != null) {
                try {
                    tripCount = Integer.parseInt(tripCountValue.toString());
                } catch (NumberFormatException ignored) {
                    tripCount = 1;
                }
            }
            countMap.put(destination, countMap.getOrDefault(destination, 0) + tripCount);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        countMap.forEach((destination, tripCount) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("destination", destination);
            item.put("tripCount", tripCount);
            result.add(item);
        });
        result.sort(Comparator.comparingInt((Map<String, Object> item) -> ((Number) item.get("tripCount")).intValue()).reversed());
        return result;
    }

    @Override
    @Transactional
    public void updateAvatar(String userId, String avatarUrl) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            user = new User();
            user.setId(userId);
            user.setAvatar(avatarUrl);
            userRepository.insert(user);
        } else {
            user.setAvatar(avatarUrl);
            userRepository.updateById(user);
        }
        log.info("用户头像更新成功：userId={}, avatarUrl={}", userId, avatarUrl);
    }

    @Override
    @Transactional
    public void updateCoverImage(String userId, String coverImageUrl) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            user = new User();
            user.setId(userId);
            user.setCoverImage(coverImageUrl);
            userRepository.insert(user);
        } else {
            user.setCoverImage(coverImageUrl);
            userRepository.updateById(user);
        }
        log.info("用户封面图更新成功：userId={}, coverImageUrl={}", userId, coverImageUrl);
    }
}
