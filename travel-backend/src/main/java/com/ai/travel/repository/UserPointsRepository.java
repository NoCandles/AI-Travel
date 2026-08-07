package com.ai.travel.repository;

import com.ai.travel.entity.UserPoints;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;

@Mapper
public interface UserPointsRepository extends BaseMapper<UserPoints> {
    
    UserPoints selectByUserId(@Param("userId") String userId);
}
