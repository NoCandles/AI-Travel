package com.ai.travel.admin.repository;

import com.ai.travel.admin.entity.AdminUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserRepository extends BaseMapper<AdminUser> {
}
