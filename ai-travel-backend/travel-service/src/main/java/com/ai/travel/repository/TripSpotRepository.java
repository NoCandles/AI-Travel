package com.ai.travel.repository;

import com.ai.travel.entity.TripSpot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface TripSpotRepository extends BaseMapper<TripSpot> {

    @Select("SELECT * FROM trip_spots WHERE trip_day_id = #{tripDayId} ORDER BY order_num")
    List<TripSpot> findByTripDayId(@Param("tripDayId") String tripDayId);

    @Delete("DELETE FROM trip_spots WHERE trip_day_id = #{tripDayId}")
    void deleteByTripDayId(@Param("tripDayId") String tripDayId);
}
