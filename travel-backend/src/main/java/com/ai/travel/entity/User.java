package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("users")
public class User implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String nickname;

    private String avatar;

    private Integer gender;

    private String city;

    private String signature;

    private String country;

    private String province;

    private String openId;

    private String phone;

    private String password;

    /** 偏好设置 JSON 字符串 */
    private String preferences;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
