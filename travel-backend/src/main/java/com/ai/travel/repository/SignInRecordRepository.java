package com.ai.travel.repository;

import com.ai.travel.entity.SignInRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SignInRecordRepository extends BaseMapper<SignInRecord> {
    
    boolean existsByUserIdAndSignInDate(@Param("userId") String userId, 
            @Param("signInDate") LocalDate signInDate);
    
    List<SignInRecord> selectByUserIdOrderBySignInDateDesc(@Param("userId") String userId, 
            @Param("offset") Integer offset, 
            @Param("limit") Integer limit);
}
