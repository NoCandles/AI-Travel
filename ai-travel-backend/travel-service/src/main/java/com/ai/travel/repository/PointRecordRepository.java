package com.ai.travel.repository;

import com.ai.travel.entity.PointRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PointRecordRepository extends BaseMapper<PointRecord> {
    
    List<PointRecord> selectByUserIdOrderByCreatedAtDesc(@Param("userId") String userId, 
            @Param("offset") Integer offset, 
            @Param("limit") Integer limit);
    
    Long countByUserIdAndSourceAndDate(@Param("userId") String userId, 
            @Param("source") String source, 
            @Param("date") LocalDate date);
}
