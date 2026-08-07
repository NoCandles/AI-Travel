package com.ai.travel.repository;

import com.ai.travel.entity.TripPublish;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TripPublishRepository extends BaseMapper<TripPublish> {

    /**
     * 原子浏览量自增（避免 READ-MODIFY-WRITE 竞态条件）
     */
    @Update("UPDATE trip_publish SET view_count = COALESCE(view_count, 0) + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") String id);
}
