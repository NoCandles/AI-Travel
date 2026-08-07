package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** 封面图 URL */
    private String coverImage;

    private Integer gender;

    private String city;

    private String signature;

    private String country;

    private String province;

    private String openId;

    private String phone;

    @JsonIgnore
    private String password;

    /** 偏好设置 JSON 字符串 */
    private String preferences;

    /** 系统设置 JSON 字符串（通知开关、定位开关等） */
    private String settings;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
