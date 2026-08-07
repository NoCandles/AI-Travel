package com.ai.travel.repository;

import com.ai.travel.entity.Attraction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttractionRepository extends BaseMapper<Attraction> {
}
