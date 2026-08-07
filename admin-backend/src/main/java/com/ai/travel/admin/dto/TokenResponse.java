package com.ai.travel.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenResponse {

    private String token;
    private String adminId;
    private String username;
    private String nickname;
    private String role;
    private Long expiresIn;
}
