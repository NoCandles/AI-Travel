package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_points")
public class UserPoints {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String userId;
    
    private Integer totalPoints = 0;
    
    private Integer availablePoints = 0;
    
    private Integer usedPoints = 0;
    
    private Integer level = 1;
    
    private Integer signInStreak = 0;
    
    private LocalDate lastSignInDate;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
