package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sign_in_record")
public class SignInRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String userId;
    
    private LocalDate signInDate;
    
    private Integer pointsEarned = 5;
    
    private LocalDateTime createdAt;
}
