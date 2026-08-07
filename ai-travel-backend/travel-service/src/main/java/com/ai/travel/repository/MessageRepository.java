package com.ai.travel.repository;

import com.ai.travel.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MessageRepository extends BaseMapper<Message> {

    List<Message> findByUserIdAndType(@Param("userId") String userId,
                                        @Param("type") String type,
                                        @Param("offset") Integer offset,
                                        @Param("limit") Integer limit);

    Long countByUserIdAndType(@Param("userId") String userId,
                               @Param("type") String type,
                               @Param("isRead") Integer isRead);

    int markAsRead(@Param("id") Long id, @Param("userId") String userId);

    int markAllAsRead(@Param("userId") String userId, @Param("type") String type);
}
