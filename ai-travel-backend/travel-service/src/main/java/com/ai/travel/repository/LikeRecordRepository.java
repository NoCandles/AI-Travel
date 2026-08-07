package com.ai.travel.repository;

import com.ai.travel.entity.LikeRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface LikeRecordRepository extends BaseMapper<LikeRecord> {

    @Select("SELECT COUNT(*) FROM like_record WHERE target_id = #{targetId} AND target_type = #{targetType} AND action_type = #{actionType}")
    long countByTarget(@Param("targetId") String targetId,
                       @Param("targetType") String targetType,
                       @Param("actionType") String actionType);

    @Select("SELECT COUNT(*) FROM like_record WHERE user_id = #{userId} AND target_id = #{targetId} AND target_type = #{targetType} AND action_type = #{actionType}")
    long exists(@Param("userId") String userId,
                @Param("targetId") String targetId,
                @Param("targetType") String targetType,
                @Param("actionType") String actionType);

    @Select("SELECT target_id FROM like_record WHERE user_id = #{userId} AND target_type = #{targetType} AND action_type = #{actionType} ORDER BY created_at DESC")
    List<String> findTargetIdsByUserId(@Param("userId") String userId,
                                        @Param("targetType") String targetType,
                                        @Param("actionType") String actionType);

    /**
     * 批量查询：给定 target ID 列表，返回当前用户已交互的 target ID 集合
     * 用于 N+1 优化
     */
    @Select("<script>" +
            "SELECT target_id FROM like_record" +
            " WHERE user_id = #{userId} AND target_type = #{targetType}" +
            " AND action_type = #{actionType}" +
            " AND target_id IN <foreach collection='targetIds' item='id' open='(' separator=',' close=')'>" +
            " #{id}" +
            " </foreach>" +
            "</script>")
    List<String> findTargetIdsIn(@Param("userId") String userId,
                                  @Param("targetType") String targetType,
                                  @Param("actionType") String actionType,
                                  @Param("targetIds") List<String> targetIds);
}