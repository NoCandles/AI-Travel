package com.ai.travel.repository;

import com.ai.travel.entity.TripPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface TripPlanRepository extends BaseMapper<TripPlan> {

    /** 查询用户去过的不重复目的地 */
    @Select("SELECT DISTINCT destination FROM trip_plans WHERE user_id = #{userId} AND destination IS NOT NULL AND destination != ''")
    List<String> findDistinctDestinationsByUserId(String userId);

    /** 查询已完成行程的不重复目的地 */
    @Select("SELECT DISTINCT destination FROM trip_plans WHERE user_id = #{userId} AND status = 'COMPLETED' AND destination IS NOT NULL AND destination != ''")
    List<String> findCompletedDestinationsByUserId(String userId);

    /** 查询各已完成目的地去过几次 */
    @Select("SELECT destination, COUNT(*) as tripCount FROM trip_plans WHERE user_id = #{userId} AND status = 'COMPLETED' AND destination IS NOT NULL AND destination != '' GROUP BY destination ORDER BY tripCount DESC")
    List<java.util.Map<String, Object>> countCompletedTripsByDestination(String userId);
}
