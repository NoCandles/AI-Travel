package com.ai.travel.repository;

import com.ai.travel.entity.UserUnlockedTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserUnlockedTemplateRepository extends BaseMapper<UserUnlockedTemplate> {
    
    boolean existsByUserIdAndTemplateId(@Param("userId") String userId, 
            @Param("templateId") String templateId);
    
    List<UserUnlockedTemplate> selectByUserId(@Param("userId") String userId);
}
