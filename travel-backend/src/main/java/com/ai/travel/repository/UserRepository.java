package com.ai.travel.repository;

import com.ai.travel.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRepository extends BaseMapper<User> {

    /**
     * 根据手机号查找用户
     */
    @Select("SELECT * FROM users WHERE phone = #{phone} LIMIT 1")
    User findByPhone(String phone);

    /**
     * 根据 OpenID 查找用户
     */
    @Select("SELECT * FROM users WHERE open_id = #{openId} LIMIT 1")
    User findByOpenId(String openId);
}
