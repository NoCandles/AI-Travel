package com.ai.travel.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class UserProfileDTO implements Serializable {

    private String id;

    private String nickname;

    private String avatar;

    private Integer gender;

    private String city;

    private String signature;

    private String country;

    private String province;
}
