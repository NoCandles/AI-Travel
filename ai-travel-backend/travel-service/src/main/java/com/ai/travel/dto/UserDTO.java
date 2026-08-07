package com.ai.travel.dto;

import lombok.Data;

@Data
public class UserDTO {
    private String id;
    private String nickname;
    private String avatar;
    private String signature;
    private String city;
    private Boolean followed;
}
