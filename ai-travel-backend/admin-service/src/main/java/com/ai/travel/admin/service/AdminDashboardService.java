package com.ai.travel.admin.service;

import com.ai.travel.admin.dto.DashboardOverviewDTO;
import com.ai.travel.admin.dto.DashboardTrendDTO;
import com.ai.travel.admin.entity.view.*;
import com.ai.travel.admin.repository.view.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserViewRepository userViewRepository;
    private final TripPlanViewRepository tripPlanViewRepository;
    private final TripPublishViewRepository tripPublishViewRepository;
    private final CommentViewRepository commentViewRepository;

    /**
     * 数据总览
     */
    public DashboardOverviewDTO overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long totalUsers = userViewRepository.selectCount(null);
        long todayNewUsers = userViewRepository.selectCount(
                new LambdaQueryWrapper<UserView>().ge(UserView::getCreatedAt, todayStart));

        long totalTrips = tripPlanViewRepository.selectCount(null);
        long todayTrips = tripPlanViewRepository.selectCount(
                new LambdaQueryWrapper<TripPlanView>().ge(TripPlanView::getCreatedAt, todayStart));

        long totalPublished = tripPublishViewRepository.selectCount(
                new LambdaQueryWrapper<TripPublishView>().eq(TripPublishView::getStatus, 1));

        long totalComments = commentViewRepository.selectCount(null);

        // 今日活跃用户：今天有操作的用户（创建行程/发布/评论）
        long todayActiveUsers = userViewRepository.selectCount(
                new LambdaQueryWrapper<UserView>()
                        .ge(UserView::getCreatedAt, todayStart)); // 简化为新增即活跃

        return DashboardOverviewDTO.builder()
                .totalUsers(totalUsers)
                .todayNewUsers(todayNewUsers)
                .totalTrips(totalTrips)
                .todayTrips(todayTrips)
                .totalPublished(totalPublished)
                .totalComments(totalComments)
                .todayActiveUsers(todayActiveUsers)
                .build();
    }

    /**
     * 近30天趋势
     */
    public List<DashboardTrendDTO> trends() {
        List<DashboardTrendDTO> list = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 29; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

            long newUsers = userViewRepository.selectCount(
                    new LambdaQueryWrapper<UserView>()
                            .ge(UserView::getCreatedAt, dayStart)
                            .lt(UserView::getCreatedAt, dayEnd));

            long newTrips = tripPlanViewRepository.selectCount(
                    new LambdaQueryWrapper<TripPlanView>()
                            .ge(TripPlanView::getCreatedAt, dayStart)
                            .lt(TripPlanView::getCreatedAt, dayEnd));

            long newPublish = tripPublishViewRepository.selectCount(
                    new LambdaQueryWrapper<TripPublishView>()
                            .ge(TripPublishView::getCreatedAt, dayStart)
                            .lt(TripPublishView::getCreatedAt, dayEnd));

            long newComments = commentViewRepository.selectCount(
                    new LambdaQueryWrapper<CommentView>()
                            .ge(CommentView::getCreatedAt, dayStart)
                            .lt(CommentView::getCreatedAt, dayEnd));

            list.add(DashboardTrendDTO.builder()
                    .date(day.format(fmt))
                    .newUsers(newUsers)
                    .newTrips(newTrips)
                    .newPublish(newPublish)
                    .newComments(newComments)
                    .build());
        }
        return list;
    }
}
