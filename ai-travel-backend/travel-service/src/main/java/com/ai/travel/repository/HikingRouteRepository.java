package com.ai.travel.repository;

import com.ai.travel.entity.HikingRoute;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HikingRouteRepository extends BaseMapper<HikingRoute> {

    /** 递增浏览量（单条 SQL，无需先查询） */
    @Update("UPDATE hiking_route SET view_count = IFNULL(view_count, 0) + 1 WHERE id = #{id}")
    void incrementViewCount(String id);
}
