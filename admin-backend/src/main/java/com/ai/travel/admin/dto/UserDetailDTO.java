package com.ai.travel.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailDTO {

    private String id;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String city;
    private String signature;
    private String phone;
    private Integer status;
    private String createdAt;

    /** 行程数 */
    private long tripCount;
    /** 发布数 */
    private long publishCount;
    /** 积分 */
    private Integer points;
    /** 等级 */
    private String level;
    /** 关注数 */
    private long followingCount;
    /** 粉丝数 */
    private long followerCount;
}
