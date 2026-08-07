package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户视图 — admin 只读映射 users 表
 */
@Data
@TableName("users")
public class UserView {
    private String id;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String city;
    private String signature;
    private String phone;
    private String openId;
    private String preferences;
    private Integer status;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
