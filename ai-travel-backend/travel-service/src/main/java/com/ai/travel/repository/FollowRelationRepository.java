package com.ai.travel.repository;

import com.ai.travel.entity.FollowRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FollowRelationRepository extends BaseMapper<FollowRelation> {

    /**
     * 批量查询：给定 following ID 列表，返回当前用户已关注的 ID 集合
     * 用于 N+1 优化
     */
    @Select("<script>" +
            "SELECT following_id FROM follow_relation" +
            " WHERE follower_id = #{followerId}" +
            " AND following_id IN <foreach collection='followingIds' item='id' open='(' separator=',' close=')'>" +
            " #{id}" +
            " </foreach>" +
            "</script>")
    List<String> findFollowingIdsIn(@Param("followerId") String followerId,
                                     @Param("followingIds") List<String> followingIds);

    @Select("SELECT following_id FROM follow_relation WHERE follower_id = #{followerId} ORDER BY created_at DESC")
    List<String> findFollowingIds(@Param("followerId") String followerId);

    @Select("SELECT follower_id FROM follow_relation WHERE following_id = #{followingId} ORDER BY created_at DESC")
    List<String> findFollowerIds(@Param("followingId") String followingId);

    @Select("SELECT COUNT(1) FROM follow_relation fr" +
            " INNER JOIN users u ON u.id = fr.following_id" +
            " WHERE fr.follower_id = #{followerId}" +
            " AND fr.following_id <> #{followerId}")
    Long countValidFollowing(@Param("followerId") String followerId);

    @Select("SELECT COUNT(1) FROM follow_relation" +
            " WHERE following_id = #{userId}" +
            " AND follower_id <> #{userId}")
    Long countFollowers(@Param("userId") String userId);
}
