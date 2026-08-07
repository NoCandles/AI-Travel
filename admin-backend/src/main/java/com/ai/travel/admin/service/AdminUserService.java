package com.ai.travel.admin.service;

import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.dto.UserDetailDTO;
import com.ai.travel.admin.entity.view.UserView;
import com.ai.travel.admin.entity.view.TripPlanView;
import com.ai.travel.admin.entity.view.TripPublishView;
import com.ai.travel.admin.repository.view.UserViewRepository;
import com.ai.travel.admin.repository.view.TripPlanViewRepository;
import com.ai.travel.admin.repository.view.TripPublishViewRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserViewRepository userViewRepository;
    private final TripPlanViewRepository tripPlanViewRepository;
    private final TripPublishViewRepository tripPublishViewRepository;

    /**
     * 分页查询用户列表
     */
    public PageResult<UserView> listUsers(int page, int size, String keyword, Integer status) {
        LambdaQueryWrapper<UserView> w = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isEmpty()) {
            w.and(w2 -> w2.like(UserView::getNickname, keyword)
                    .or().like(UserView::getPhone, keyword)
                    .or().like(UserView::getId, keyword));
        }
        if (status != null) {
            w.eq(UserView::getStatus, status);
        }
        w.orderByDesc(UserView::getCreatedAt);

        Page<UserView> p = userViewRepository.selectPage(new Page<>(page, size), w);
        return PageResult.of(p);
    }

    /**
     * 用户详情
     */
    public UserDetailDTO getUserDetail(String userId) {
        UserView user = userViewRepository.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        long tripCount = tripPlanViewRepository.selectCount(
                new LambdaQueryWrapper<TripPlanView>().eq(TripPlanView::getUserId, userId));
        long publishCount = tripPublishViewRepository.selectCount(
                new LambdaQueryWrapper<TripPublishView>().eq(TripPublishView::getUserId, userId));

        return UserDetailDTO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .gender(user.getGender())
                .city(user.getCity())
                .signature(user.getSignature())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .tripCount(tripCount)
                .publishCount(publishCount)
                .build();
    }

    /**
     * 封禁/解封用户
     */
    public void updateUserStatus(String userId, Integer status) {
        UserView user = userViewRepository.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setStatus(status);
        userViewRepository.updateById(user);
    }
}
