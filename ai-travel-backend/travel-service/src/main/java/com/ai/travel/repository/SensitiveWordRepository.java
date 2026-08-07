package com.ai.travel.repository;

import com.ai.travel.entity.SensitiveWord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SensitiveWordRepository extends BaseMapper<SensitiveWord> {
}
