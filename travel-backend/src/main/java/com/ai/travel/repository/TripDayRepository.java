package com.ai.travel.repository;

import com.ai.travel.entity.TripDay;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface TripDayRepository extends BaseMapper<TripDay> {

    @Select("SELECT * FROM trip_days WHERE trip_id = #{tripId} ORDER BY day")
    List<TripDay> findByTripId(@Param("tripId") String tripId);
}
