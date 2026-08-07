package com.ai.travel.repository;

import com.ai.travel.entity.LevelConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface LevelConfigRepository extends BaseMapper<LevelConfig> {
    
    LevelConfig selectByLevel(@Param("level") Integer level);
    
    LevelConfig selectByPoints(@Param("points") Integer points);
}
