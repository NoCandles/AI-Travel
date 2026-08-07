package com.ai.travel.admin.service;

import com.ai.travel.admin.dto.PageResult;
import com.ai.travel.admin.dto.TripDetailDTO;
import com.ai.travel.admin.entity.view.TripDayView;
import com.ai.travel.admin.entity.view.TripPlanView;
import com.ai.travel.admin.entity.view.TripSpotView;
import com.ai.travel.admin.repository.view.TripDayViewRepository;
import com.ai.travel.admin.repository.view.TripPlanViewRepository;
import com.ai.travel.admin.repository.view.TripSpotViewRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTripService {

    private final TripPlanViewRepository tripPlanRepository;
    private final TripDayViewRepository tripDayRepository;
    private final TripSpotViewRepository tripSpotRepository;

    public PageResult<TripPlanView> listTrips(int page, int size, String keyword, String status, String userId) {
        LambdaQueryWrapper<TripPlanView> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            w.and(w2 -> w2.like(TripPlanView::getName, keyword)
                    .or().like(TripPlanView::getDestination, keyword));
        }
        if (status != null && !status.isEmpty()) {
            w.eq(TripPlanView::getStatus, status);
        }
        if (userId != null && !userId.isEmpty()) {
            w.eq(TripPlanView::getUserId, userId);
        }
        w.orderByDesc(TripPlanView::getCreatedAt);
        return PageResult.of(tripPlanRepository.selectPage(new Page<>(page, size), w));
    }

    public TripDetailDTO getTripDetail(String id) {
        TripPlanView plan = tripPlanRepository.selectById(id);
        if (plan == null) throw new RuntimeException("行程不存在");

        List<TripDayView> days = tripDayRepository.selectList(
                new LambdaQueryWrapper<TripDayView>()
                        .eq(TripDayView::getTripId, id)
                        .orderByAsc(TripDayView::getNumber));

        List<List<TripSpotView>> spotsByDay = new ArrayList<>();
        for (TripDayView day : days) {
            List<TripSpotView> spots = tripSpotRepository.selectList(
                    new LambdaQueryWrapper<TripSpotView>()
                            .eq(TripSpotView::getTripDayId, day.getId())
                            .orderByAsc(TripSpotView::getOrderNum));
            spotsByDay.add(spots);
        }

        return TripDetailDTO.builder()
                .id(plan.getId())
                .name(plan.getName())
                .destination(plan.getDestination())
                .userId(plan.getUserId())
                .status(plan.getStatus())
                .travelMode(plan.getTravelMode())
                .createdAt(plan.getCreatedAt() != null ? plan.getCreatedAt().toString() : null)
                .days(days)
                .spotsByDay(spotsByDay)
                .build();
    }

    @Transactional
    public void deleteTrip(String id) {
        TripPlanView plan = tripPlanRepository.selectById(id);
        if (plan == null) throw new RuntimeException("行程不存在");

        List<TripDayView> days = tripDayRepository.selectList(
                new LambdaQueryWrapper<TripDayView>().eq(TripDayView::getTripId, id));
        for (TripDayView day : days) {
            tripSpotRepository.delete(
                    new LambdaQueryWrapper<TripSpotView>().eq(TripSpotView::getTripDayId, day.getId()));
            tripDayRepository.deleteById(day.getId());
        }
        tripPlanRepository.deleteById(id);
    }
}
