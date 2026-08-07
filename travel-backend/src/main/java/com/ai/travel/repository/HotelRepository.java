package com.ai.travel.repository;

import com.ai.travel.entity.Hotel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HotelRepository extends BaseMapper<Hotel> {
}
