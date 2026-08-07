package com.ai.travel.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String phone;
    private String wechatCode;
    private String newPassword;
}
